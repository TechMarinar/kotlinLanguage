/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.inline.InlinedFunctionReference
import org.jetbrains.kotlin.backend.common.phaser.makeIrModulePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.functionInliningPhase
import org.jetbrains.kotlin.backend.jvm.ir.isInlineParameter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.getArgumentsWithIr
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

    private fun IrExpression.wasExplicitlyInlined(): Boolean {
        return this is IrReturnableBlock && this.statements.firstOrNull() is IrInlineMarker &&
                inlineFunctionSymbol?.owner?.origin != IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
    }

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.symbol == context.ir.symbols.singleArgumentInlineFunction) {
            return expression
        }
        return super.visitCall(expression)
    }

    private fun IrReturnableBlock.collectDeclarationsThatMustBeRegenerated(): Set<IrAttributeContainer> {
        val classesToRegenerate = mutableSetOf<IrAttributeContainer>()
        this.acceptVoid(object : IrElementVisitorVoid {
            private val containersStack = mutableListOf<IrAttributeContainer>()
            private val inlinableParameters = mutableListOf<IrValueParameter>()

            fun IrContainerExpression.getInlinableParameters(): List<IrValueParameter> {
                val inlineMarker = this.statements.first() as IrInlineMarker
                val call = inlineMarker.inlineCall
                return call.getArgumentsWithIr()
                    .filter { (param, arg) ->
                        param.isInlineParameter() && arg is IrFunctionExpression ||
                                arg is IrGetValue && arg.symbol.owner in inlinableParameters
                    }
                    .map { it.first } +
                        call.symbol.owner.valueParameters.filterIndexed { index, param ->
                            call.getValueArgument(index) == null && param.isInlineParameter()
                        }
            }

            private fun saveDeclarationsFromStackIntoRegenerationPool() {
                containersStack.forEach {
                    classesToRegenerate += it
                }
            }

            override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)

            override fun visitClassReference(expression: IrClassReference) {
                if (expression.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                super.visitClassReference(expression)
            }

            override fun visitClass(declaration: IrClass) {
                containersStack += declaration
                if (declaration.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                declaration.attributeOwnerIdBeforeInline?.acceptChildrenVoid(this) // check if we need to save THIS declaration
                declaration.acceptChildrenVoid(this) // check if we need to save INNER declarations
                containersStack.removeLast()
            }

            override fun visitFunctionExpression(expression: IrFunctionExpression) {
                containersStack += expression
                expression.attributeOwnerIdBeforeInline?.acceptChildrenVoid(this)
                expression.acceptChildrenVoid(this)
                containersStack.removeLast()
            }

            override fun visitFunctionReference(expression: IrFunctionReference) {
                containersStack += expression
                expression.attributeOwnerIdBeforeInline?.acceptChildrenVoid(this)
                expression.acceptChildrenVoid(this)
                containersStack.removeLast()
            }

            override fun visitTypeOperator(expression: IrTypeOperatorCall) {
                if (expression.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                super.visitTypeOperator(expression)
            }

            override fun visitGetValue(expression: IrGetValue) {
                super.visitGetValue(expression)
                if (expression.symbol.owner in inlinableParameters) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
                if (expression.type.getClass()?.let { classesToRegenerate.contains(it) } == true) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
            }

            override fun visitCall(expression: IrCall) {
                if (expression.symbol == context.ir.symbols.singleArgumentInlineFunction) {
                    (expression.getValueArgument(0) as IrFunctionExpression).function.acceptVoid(this)
                    return
                }

                if (expression.hasReifiedTypeParameters() || expression.origin is InlinedFunctionReference) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
                super.visitCall(expression)
            }

            override fun visitContainerExpression(expression: IrContainerExpression) {
                if (expression.wasExplicitlyInlined()) {
                    val additionalInlinableParameters = expression.getInlinableParameters()
                    inlinableParameters.addAll(additionalInlinableParameters)
                    super.visitContainerExpression(expression)
                    inlinableParameters.dropLast(additionalInlinableParameters.size)
                    return
                }

                super.visitContainerExpression(expression)
            }
        })
        return classesToRegenerate
    }

    private fun IrAttributeContainer.hasReifiedTypeParameters(): Boolean {
        var hasReified = false

        fun IrType.recursiveWalkDown(visitor: IrElementVisitorVoid) {
            this@recursiveWalkDown.classifierOrNull?.owner?.acceptVoid(visitor)
            (this@recursiveWalkDown as? IrSimpleType)?.arguments?.forEach { it.typeOrNull?.recursiveWalkDown(visitor) }
        }

        this.attributeOwnerIdBeforeInline?.acceptVoid(object : IrElementVisitorVoid {
            private val visitedClasses = mutableSetOf<IrClass>()

            override fun visitElement(element: IrElement) {
                if (hasReified) return
                element.acceptChildrenVoid(this)
            }

            override fun visitTypeParameter(declaration: IrTypeParameter) {
                hasReified = hasReified || declaration.isReified
                super.visitTypeParameter(declaration)
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
                    // Technically we need to generate SEQUENCE of `element.attributeOwnerIdBeforeInline` and find the original one.
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