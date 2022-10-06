/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.getAdditionalStatementsFromInlinedBlock
import org.jetbrains.kotlin.backend.common.ir.getOriginalStatementsFromInlinedBlock
import org.jetbrains.kotlin.backend.common.lower.parentsWithSelf
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.functionInliningPhase
import org.jetbrains.kotlin.backend.jvm.localDeclarationsPhase
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrInlineMarker
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

internal val removeDuplicatedInlinedLocalClasses = makeIrFilePhase(
    ::RemoveDuplicatedInlinedLocalClassesLowering,
    name = "RemoveDuplicatedInlinedLocalClasses",
    description = "Drop excess local classes that were copied by ir inliner",
    prerequisite = setOf(functionInliningPhase, localDeclarationsPhase)
)

// There are three types of inlined local classes:
// 1. MUST BE regenerated according to set of rules in AnonymousObjectTransformationInfo.
// They all have `attributeOwnerIdBeforeInline != null`.
// 2. MUST NOT BE regenerated and MUST BE CREATED only once because they are copied from call site.
// This lambda will not exist after inline, so we copy declaration into new temporary inline call `singleArgumentInlineFunction`.
// 3. MUST NOT BE created at all because will be created at callee site.
// This lowering drops declarations that correspond to second and third type.
class RemoveDuplicatedInlinedLocalClassesLowering(val context: JvmBackendContext) : IrElementTransformer<Boolean>, FileLoweringPass {
    private val inlineStack = mutableListOf<IrInlineMarker>()

    override fun lower(irFile: IrFile) {
        irFile.transformChildren(this, false)
    }

    override fun visitCall(expression: IrCall, data: Boolean): IrElement {
        if (expression.symbol == context.ir.symbols.singleArgumentInlineFunction) return expression
        return super.visitCall(expression, data)
    }

    override fun visitBlock(expression: IrBlock, data: Boolean): IrExpression {
        if (expression.statements.isNotEmpty() && expression.statements.first() is IrInlineMarker) {
            val marker = expression.statements.first() as IrInlineMarker
            inlineStack += marker
            expression.getAdditionalStatementsFromInlinedBlock().forEach { it.transform(this, false) }
            expression.getOriginalStatementsFromInlinedBlock().forEach { it.transform(this, true) }
            inlineStack.removeLast()
            return expression
        }

        return super.visitBlock(expression, data)
    }

    // Basically we want to remove all anonymous classes after inline
    // Except for those that are required to present as a copy, see `isRequiredToBeDeclaredOnCallSite`
    override fun visitClass(declaration: IrClass, data: Boolean): IrStatement {
        // After first two checks we are sure that class declaration is unchanged and is declared either on call site or on callee site
        if (inlineStack.isEmpty() || declaration.attributeOwnerIdBeforeInline != null || declaration.isRequiredToBeDeclaredOnCallSite(data)) {
            return super.visitClass(declaration, data)
        }

        // TODO big note. Here we drop anonymous class declaration but there still present a constructor call that has reference to this class.
        //  Everything works fine because somewhere in code we still have class declaration with the same name. So this doesn't ruin code
        //  generation and will not drop on runtime, but this is something to be aware of.
        return IrCompositeImpl(declaration.startOffset, declaration.endOffset, context.irBuiltIns.unitType)
    }

    override fun visitFunctionReference(expression: IrFunctionReference, data: Boolean): IrElement {
        expression.symbol.owner.accept(this, data)
        return super.visitFunctionReference(expression, data)
    }

    // This function return `true` if given declaration must present on call site.
    // Everything outside "additional" blocks can be dropped
    // Declarations inside these blocks must remain if it is not a default one
    // To understand that we can check parent of original declaration
    private fun IrAttributeContainer.isRequiredToBeDeclaredOnCallSite(originallyDeclaredOnCallSite: Boolean): Boolean {
        if (originallyDeclaredOnCallSite) return false
        val declaration = when (val original = this.attributeOwnerId) {
            is IrClass -> original
            is IrFunctionExpression -> original.function
            is IrFunctionReference -> return true // `originallyDeclaredOnCallSite` ensures that this reference was declared on call site
            else -> return false
        }
        return declaration.parentsWithSelf.any { it == inlineStack.last().inlinedAt }
    }
}