/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.wasExplicitlyInlined
import org.jetbrains.kotlin.backend.common.lower.inline.InlinedFunctionReference
import org.jetbrains.kotlin.backend.common.phaser.makeIrModulePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.functionInliningPhase
import org.jetbrains.kotlin.backend.jvm.ir.isInlineParameter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.getAllArgumentsWithIr
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

internal val markNecessaryInlinedClassesAsRegenerated = makeIrModulePhase(
    ::MarkNecessaryInlinedClassesAsRegeneratedLowering,
    name = "MarkNecessaryInlinedClassesAsRegeneratedLowering",
    description = "Will scan all inlined functions and mark anonymous objects that must be later regenerated at backend",
    prerequisite = setOf(functionInliningPhase, createSeparateCallForInlinedLambdas)
)

class MarkNecessaryInlinedClassesAsRegeneratedLowering(val context: JvmBackendContext) : IrElementTransformerVoid(), FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid()
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        if (expression.wasExplicitlyInlined()) {
            val mustBeRegenerated = (expression as IrReturnableBlock).collectDeclarationsThatMustBeRegenerated()
            expression.setUpCorrectAttributesForAllInnerElements(mustBeRegenerated)
            return expression
        }

        return super.visitBlock(expression)
    }

    private fun IrReturnableBlock.collectDeclarationsThatMustBeRegenerated(): Set<IrAttributeContainer> {
        val classesToRegenerate = mutableSetOf<IrAttributeContainer>()
        this.acceptVoid(object : IrElementVisitorVoid {
            private val containersStack = mutableListOf<IrAttributeContainer>()
            private val inlinableParameters = mutableListOf<IrValueParameter>()
            private val reifiedArguments = mutableListOf<IrType>()
            private var processingBeforeInlineDeclaration = false

            fun IrContainerExpression.getInlinableParameters(): List<IrValueParameter> {
                val call = (this.statements.first() as IrInlineMarker).inlineCall
                return call.getAllArgumentsWithIr()
                    .filter { (param, arg) ->
                        param.isInlineParameter() && (arg ?: param.defaultValue?.expression) is IrFunctionExpression ||
                                arg is IrGetValue && arg.symbol.owner in inlinableParameters
                    }
                    .map { it.first }
            }

            fun IrContainerExpression.getReifiedArguments(): List<IrType> {
                val call = (this.statements.first() as IrInlineMarker).inlineCall
                return call.symbol.owner.typeParameters.mapIndexedNotNull { index, param ->
                    call.getTypeArgument(index)?.takeIf { param.isReified }
                }
            }

            private fun saveDeclarationsFromStackIntoRegenerationPool() {
                containersStack.forEach {
                    classesToRegenerate += it
                }
            }

            override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)

            private fun visitAnonymousDeclaration(declaration: IrAttributeContainer) {
                containersStack += declaration
                if (declaration.hasReifiedTypeArguments(reifiedArguments)) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
                if (!processingBeforeInlineDeclaration) {
                    processingBeforeInlineDeclaration = true
                    declaration.attributeOwnerIdBeforeInline?.acceptChildrenVoid(this) // check if we need to save THIS declaration
                    processingBeforeInlineDeclaration = false
                }
                declaration.acceptChildrenVoid(this) // check if we need to save INNER declarations
                containersStack.removeLast()
            }

            override fun visitClass(declaration: IrClass) {
                visitAnonymousDeclaration(declaration)
            }

            override fun visitFunctionExpression(expression: IrFunctionExpression) {
                visitAnonymousDeclaration(expression)
            }

            override fun visitFunctionReference(expression: IrFunctionReference) {
                visitAnonymousDeclaration(expression)
            }

            override fun visitGetValue(expression: IrGetValue) {
                super.visitGetValue(expression)
                if (
                    expression.symbol.owner in inlinableParameters ||
                    expression.type.getClass()?.let { classesToRegenerate.contains(it) } == true
                ) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
            }

            override fun visitCall(expression: IrCall) {
                if (expression.symbol == context.ir.symbols.singleArgumentInlineFunction) {
                    (expression.getValueArgument(0) as IrFunctionExpression).function.acceptVoid(this)
                    return
                }

                if (expression.origin is InlinedFunctionReference) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
                super.visitCall(expression)
            }

            override fun visitContainerExpression(expression: IrContainerExpression) {
                if (expression.wasExplicitlyInlined()) {
                    val additionalInlinableParameters = expression.getInlinableParameters()
                    val additionalTypeArguments = expression.getReifiedArguments()

                    inlinableParameters.addAll(additionalInlinableParameters)
                    reifiedArguments.addAll(additionalTypeArguments)
                    super.visitContainerExpression(expression)
                    inlinableParameters.dropLast(additionalInlinableParameters.size)
                    reifiedArguments.dropLast(additionalTypeArguments.size)
                    return
                }

                super.visitContainerExpression(expression)
            }
        })
        return classesToRegenerate
    }

    private fun IrAttributeContainer.hasReifiedTypeArguments(reifiedArguments: List<IrType>): Boolean {
        var hasReified = false

        fun IrType.recursiveWalkDown(visitor: IrElementVisitorVoid) {
            hasReified = hasReified || this@recursiveWalkDown in reifiedArguments
            (this@recursiveWalkDown as? IrSimpleType)?.arguments?.forEach { it.typeOrNull?.recursiveWalkDown(visitor) }
        }

        this.acceptVoid(object : IrElementVisitorVoid {
            private val visitedClasses = mutableSetOf<IrClass>()

            override fun visitElement(element: IrElement) {
                if (hasReified) return
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                if (!visitedClasses.add(declaration)) return
                declaration.superTypes.forEach { it.recursiveWalkDown(this) }
                super.visitClass(declaration)
            }

            override fun visitTypeOperator(expression: IrTypeOperatorCall) {
                expression.typeOperand.takeIf { it is IrSimpleType }?.recursiveWalkDown(this)
                super.visitTypeOperator(expression)
            }

            override fun visitCall(expression: IrCall) {
                (0 until expression.typeArgumentsCount).forEach {
                    expression.getTypeArgument(it)?.recursiveWalkDown(this)
                }
                super.visitCall(expression)
            }

            override fun visitClassReference(expression: IrClassReference) {
                expression.classType.recursiveWalkDown(this)
                super.visitClassReference(expression)
            }
        })
        return hasReified
    }

    private fun IrElement.setUpCorrectAttributesForAllInnerElements(mustBeRegenerated: Set<IrAttributeContainer>) {
        this.acceptChildrenVoid(object : IrElementVisitorVoid {
            private fun checkAndSetUpCorrectAttributes(element: IrAttributeContainer) {
                when {
                    element !in mustBeRegenerated && element.attributeOwnerIdBeforeInline != null -> element.setUpOriginalAttributes()
                    else -> element.acceptChildrenVoid(this)
                }
            }

            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                checkAndSetUpCorrectAttributes(declaration)
            }

            override fun visitFunctionExpression(expression: IrFunctionExpression) {
                checkAndSetUpCorrectAttributes(expression)
            }

            override fun visitFunctionReference(expression: IrFunctionReference) {
                checkAndSetUpCorrectAttributes(expression)
            }
        })
    }

    private fun IrElement.setUpOriginalAttributes() {
        acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                if (element is IrAttributeContainer && element.attributeOwnerIdBeforeInline != null) {
                    // Basically we need to generate SEQUENCE of `element.attributeOwnerIdBeforeInline` and find the original one.
                    //  But this is not needed because we process all functions in the same order as they were processed by FunctionInlining.
                    //  This mean that when we start to precess current container, all inner ones in SEQUENCE will already be processed.
                    element.attributeOwnerId = element.attributeOwnerIdBeforeInline!!.attributeOwnerId
                    element.attributeOwnerIdBeforeInline = null
                }
                element.acceptChildrenVoid(this)
            }
        })
    }
}