/*
 * Copyright 2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.getAdditionalStatementsFromInlinedBlock
import org.jetbrains.kotlin.backend.common.ir.putStatementsInFrontOfInlinedFunction
import org.jetbrains.kotlin.backend.common.ir.wasExplicitlyInlined
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.ir.isInlineOnly
import org.jetbrains.kotlin.codegen.inline.INLINE_FUN_VAR_SUFFIX
import org.jetbrains.kotlin.codegen.inline.coroutines.FOR_INLINE_SUFFIX
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.createTmpVariable
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name

internal val fakeInliningLocalVariablesAfterInlineLowering = makeIrFilePhase(
    ::FakeInliningLocalVariablesAfterInlineLowering,
    name = "FakeInliningLocalVariablesAfterInlineLowering",
    description = """Add fake locals to identify the range of inlined functions and lambdas. 
        |This lowering adds fake locals into already inlined blocks.""".trimMargin()
)

internal class FakeInliningLocalVariablesAfterInlineLowering(val context: JvmBackendContext) : IrElementVisitor<Unit, IrDeclaration?>, FileLoweringPass {
    private val inlinedFunctionStack = mutableListOf<IrFunction>()

//    private fun List<List<IrFunctionExpression>>.firstFlat(value: IrFunction): IrFunctionExpression {
//        this.forEach { innerList ->
//            innerList.firstOrNull { it.function == value }?.let { return it }
//        }
//        throw NoSuchElementException("Collection contains no element matching the predicate.")
//    }

    override fun lower(irFile: IrFile) {
        irFile.accept(this, null)
    }

    override fun visitElement(element: IrElement, data: IrDeclaration?) {
        val newData = if (element is IrDeclaration && element !is IrVariable) element else data
        element.acceptChildren(this, newData)
    }

//    override fun visitFunction(declaration: IrFunction, data: IrDeclaration?) {
//        if (declaration.origin == JvmLoweredDeclarationOrigin.INLINE_LAMBDA) {
//            return
//        }
//        super.visitFunction(declaration, data)
//    }
//
//    override fun visitFunctionReference(expression: IrFunctionReference, data: IrDeclaration?) {
//        super.visitFunctionReference(expression, data)
//        if (expression.origin == JvmLoweredStatementOrigin.INLINE_LAMBDA) {
//            expression.symbol.owner.acceptChildren(this, expression.symbol.owner)
//        }
//    }

    override fun visitBlock(expression: IrBlock, data: IrDeclaration?) {
        when {
            expression.wasExplicitlyInlined() -> handleInlineFunction(expression, data)
            expression.statements.firstOrNull() is IrInlineMarker -> handleInlineLambda(expression, data)
            else -> super.visitBlock(expression, data)
        }
    }

    private fun handleInlineFunction(expression: IrBlock, data: IrDeclaration?) {
        val marker = expression.statements.first() as IrInlineMarker
        val declaration = marker.callee

        inlinedFunctionStack += declaration
        super.visitBlock(expression, data)
        inlinedFunctionStack.removeLast()

        if (declaration.isInline && !declaration.origin.isSynthetic && declaration.body != null && !declaration.isInlineOnly()) {
            val currentFunctionName = context.methodSignatureMapper.mapFunctionName(declaration)
            val localName = "${JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION}$currentFunctionName"
            //declaration.addFakeLocalVariable(localName)
            with(context.createIrBuilder(data!!.symbol)) {
                val tmpVar =
                    scope.createTmpVariable(irInt(0), localName.removeSuffix(FOR_INLINE_SUFFIX), origin = IrDeclarationOrigin.DEFINED)
                expression.putStatementsInFrontOfInlinedFunction(listOf(tmpVar))
            }
        }

        expression.processLocalDeclarations()
    }

    private fun handleInlineLambda(expression: IrBlock, data: IrDeclaration?) {
        super.visitBlock(expression, data)

        val marker = expression.statements.first() as IrInlineMarker
        val callee = marker.inlinedAt as IrFunction
        val argument = marker.originalExpression!!

        //            val lambda = argument.function.symbol.owner
        val argumentToFunctionName = context.methodSignatureMapper.mapFunctionName(callee)
        val lambdaReferenceName = context.getLocalClassType(argument)!!.internalName.substringAfterLast("/")
        val localName = "${JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT}-$argumentToFunctionName-$lambdaReferenceName"
        //            lambda.addFakeLocalVariable(localName)
        with(context.createIrBuilder(data!!.symbol)) {
            val tmpVar = scope.createTmpVariable(irInt(0), localName.removeSuffix(FOR_INLINE_SUFFIX), origin = IrDeclarationOrigin.DEFINED)
            expression.putStatementsInFrontOfInlinedFunction(listOf(tmpVar))
        }

        expression.processLocalDeclarations()
    }

    private fun IrBlock.processLocalDeclarations() {
        this.getAdditionalStatementsFromInlinedBlock().forEach {
            if (it is IrVariable && it.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE) {
                it.name = Name.identifier(it.name.asString().substringAfterLast("_") + INLINE_FUN_VAR_SUFFIX)
                it.origin = IrDeclarationOrigin.DEFINED
            }
        }
    }
}
