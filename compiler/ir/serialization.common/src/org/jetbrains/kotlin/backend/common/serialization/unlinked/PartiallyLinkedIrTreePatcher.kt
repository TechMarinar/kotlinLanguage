/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.unlinked

import org.jetbrains.kotlin.backend.common.serialization.unlinked.LinkedClassifierStatus.Partially
import org.jetbrains.kotlin.backend.common.serialization.unlinked.PartialLinkageCase.*
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.IrMessageLogger.Location
import org.jetbrains.kotlin.ir.util.IrMessageLogger.Severity
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import kotlin.properties.Delegates

internal class PartiallyLinkedIrTreePatcher(
    private val builtIns: IrBuiltIns,
    private val markerTypeHandler: PartiallyLinkedMarkerTypeHandler,
    private val classifierExplorer: LinkedClassifierExplorer,
    private val messageLogger: IrMessageLogger
) {
    fun patchUsageOfUnlinkedSymbols(roots: Collection<IrElement>) {
        roots.forEach { it.transformChildrenVoid(UsageTransformer()) }
    }

    private inner class UsageTransformer : IrElementTransformerVoid() {
        private var currentFile: IrFile? = null

        override fun visitFile(declaration: IrFile): IrFile {
            currentFile = declaration
            return try {
                super.visitFile(declaration)
            } finally {
                currentFile = null
            }
        }

        override fun visitClass(declaration: IrClass): IrStatement {
            // Discover the reason why the class itself is partially linked.
            val partialLinkageReason = declaration.symbol.partialLinkageReason()
            if (partialLinkageReason != null) {
                // Transform the reason to the most appropriate linkage case.
                val partialLinkageCase = when (partialLinkageReason) {
                    is Partially.MissingClassifier -> MissingDeclaration(declaration.symbol)
                    is Partially.MissingEnclosingClass -> MissingEnclosingClass(declaration)
                    is Partially.DueToOtherClassifier -> DeclarationUsesPartiallyLinkedSymbol(declaration, partialLinkageReason.rootCause)
                }

                val anonInitializer = declaration.declarations.firstNotNullOfOrNull { it as? IrAnonymousInitializer }
                    ?: builtIns.irFactory.createAnonymousInitializer(
                        declaration.startOffset,
                        declaration.endOffset,
                        PartiallyLinkedDeclarationOrigin.MISSING_DECLARATION,
                        IrAnonymousInitializerSymbolImpl()
                    ).apply {
                        body = builtIns.irFactory.createBlockBody(declaration.startOffset, declaration.endOffset)
                        parent = declaration
                        declaration.declarations += this
                    }
                anonInitializer.body.statements.clear()
                anonInitializer.body.statements += partialLinkageCase.throwLinkageError(anonInitializer)

                declaration.superTypes = declaration.superTypes.filter { !it.isPartiallyLinkedType() }
            }

            return super.visitClass(declaration)
        }

        override fun visitFunction(declaration: IrFunction): IrStatement {
            (declaration as? IrOverridableDeclaration<*>)?.filterOverriddenSymbols()

            // IMPORTANT: It's necessary to overwrite types even if the returned p.l. reason won't be used further.
            val signaturePartialLinkageReason = declaration.rewriteTypes()

            val partialLinkageCase = when (declaration.origin) {
                PartiallyLinkedDeclarationOrigin.UNIMPLEMENTED_ABSTRACT_CALLABLE_MEMBER -> UnimplementedAbstractCallable(declaration as IrOverridableDeclaration<*>)
                PartiallyLinkedDeclarationOrigin.MISSING_DECLARATION -> MissingDeclaration(declaration.symbol)
                else -> if (signaturePartialLinkageReason != null)
                    DeclarationUsesPartiallyLinkedSymbol(declaration, signaturePartialLinkageReason)
                else
                    null
            }

            return if (partialLinkageCase != null) {
                val blockBody = declaration.body as? IrBlockBody
                    ?: builtIns.irFactory.createBlockBody(declaration.startOffset, declaration.endOffset).apply { declaration.body = this }

                blockBody.statements.clear()
                blockBody.statements += partialLinkageCase.throwLinkageError(declaration)

                declaration
            } else {
                super.visitFunction(declaration)
            }
        }

        /**
         * Returns the first [Partially] from the first encountered partially linked type.
         */
        private fun IrFunction.rewriteTypes(): Partially? {
            var result: Partially? by Delegates.vetoable(null) { _, oldValue, _ -> oldValue == null }

            fun IrValueParameter.fixType() {
                val partialLinkageReason = type.partialLinkageReason() ?: varargElementType?.partialLinkageReason()
                if (partialLinkageReason != null) {
                    type = markerTypeHandler.markerType
                    defaultValue = null
                    if (varargElementType != null) varargElementType = markerTypeHandler.markerType
                    result = partialLinkageReason
                }
            }

            extensionReceiverParameter?.fixType()
            valueParameters.forEach { it.fixType() }

            returnType.partialLinkageReason()?.let { partialLinkageReason ->
                returnType = markerTypeHandler.markerType
                result = partialLinkageReason
            }

            typeParameters.forEach { tp ->
                val partialLinkageReason = tp.superTypes.firstNotNullOfOrNull { st -> st.partialLinkageReason() }
                if (partialLinkageReason != null) {
                    tp.superTypes = listOf(markerTypeHandler.markerType)
                    result = partialLinkageReason
                }
            }

            dispatchReceiverParameter?.fixType() // The dispatcher (aka this) is intentionally the latest one.

            return result
        }

        override fun visitProperty(declaration: IrProperty): IrStatement {
            declaration.filterOverriddenSymbols()
            return super.visitProperty(declaration)
        }

        override fun visitField(declaration: IrField): IrStatement {
            return if (declaration.type.isPartiallyLinkedType()) {
                declaration.type = markerTypeHandler.markerType
                declaration.initializer = null
                declaration
            } else
                super.visitField(declaration)
        }

        override fun visitVariable(declaration: IrVariable): IrStatement {
            return if (declaration.type.isPartiallyLinkedType()) {
                declaration.type = markerTypeHandler.markerType
                declaration.initializer = null
                declaration
            } else
                super.visitVariable(declaration)
        }

        override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
            return expression.throwLinkageErrorIfPartiallyLinkedSymbolsInTypes {
                type.partialLinkageReason() ?: typeOperand.partialLinkageReason()
            } ?: super.visitTypeOperator(expression)
        }

        override fun visitVararg(expression: IrVararg): IrExpression {
            return expression.throwLinkageErrorIfPartiallyLinkedSymbolsInTypes {
                type.partialLinkageReason() ?: varargElementType.partialLinkageReason()
            } ?: super.visitVararg(expression)
        }

        override fun visitClassReference(expression: IrClassReference): IrExpression {
            return expression.throwLinkageErrorIfPartiallyLinkedSymbolsInTypes {
                type.partialLinkageReason() ?: classType.partialLinkageReason()
            } ?: super.visitClassReference(expression)
        }

        override fun visitMemberAccess(expression: IrMemberAccessExpression<*>): IrExpression {
            return expression.throwLinkageErrorIfPartiallyLinkedSymbolsInTypes {
                type.partialLinkageReason()
                    ?: (0 until typeArgumentsCount).firstNotNullOfOrNull { index -> getTypeArgument(index)?.partialLinkageReason() }
            } ?: super.visitMemberAccess(expression)
        }

        override fun visitExpression(expression: IrExpression): IrExpression {
            return expression.throwLinkageErrorIfPartiallyLinkedSymbolsInTypes { type.partialLinkageReason() }
                ?: super.visitExpression(expression)
        }

        private inline fun <T : IrExpression> T.throwLinkageErrorIfPartiallyLinkedSymbolsInTypes(
            computePartialLinkageReason: T.() -> Partially?
        ): IrCall? {
            val partialLinkageReason = computePartialLinkageReason(this) ?: return null
            return ExpressionUsesPartiallyLinkedSymbol(this, partialLinkageReason).throwLinkageError(element = this, file = currentFile)
        }
    }

    private fun IrClassifierSymbol.partialLinkageReason(): Partially? = classifierExplorer.exploreSymbol(this)

    private fun IrType.partialLinkageReason(): Partially? = classifierExplorer.exploreType(this)
    private fun IrType.isPartiallyLinkedType(): Boolean = partialLinkageReason() != null

    private fun PartialLinkageCase.throwLinkageError(declaration: IrDeclaration): IrCall =
        throwLinkageError(declaration, declaration.fileOrNull)

    private fun PartialLinkageCase.throwLinkageError(element: IrElement, file: IrFile?): IrCall {
        val errorMessage = renderErrorMessage()
        val locationInSourceCode = element.computeLocationInSourceCode(file)

        messageLogger.report(Severity.WARNING, errorMessage, locationInSourceCode) // It's OK. We log it as a warning.

        return IrCallImpl(
            startOffset = element.startOffset,
            endOffset = element.endOffset,
            type = builtIns.nothingType,
            symbol = builtIns.linkageErrorSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 1,
            origin = PARTIAL_LINKAGE_RUNTIME_ERROR
        ).apply {
            putValueArgument(0, IrConstImpl.string(startOffset, endOffset, builtIns.stringType, errorMessage))
        }
    }

    companion object {
        private fun <S : IrSymbol> IrOverridableDeclaration<S>.filterOverriddenSymbols() {
            overriddenSymbols = overriddenSymbols.filter { symbol ->
                val owner = symbol.owner as IrDeclaration
                owner.origin != PartiallyLinkedDeclarationOrigin.MISSING_DECLARATION
                        // Handle the case when the overridden declaration became private.
                        && (owner as? IrDeclarationWithVisibility)?.visibility != DescriptorVisibilities.PRIVATE
            }
        }

        private fun IrElement.computeLocationInSourceCode(currentFile: IrFile?): Location? {
            if (currentFile == null) return null

            val moduleName: String = currentFile.module.name.asString()
            val filePath: String = currentFile.fileEntry.name

            val lineNumber: Int
            val columnNumber: Int

            when (val effectiveStartOffset = startOffsetOfFirstDenotableIrElement()) {
                UNDEFINED_OFFSET -> {
                    lineNumber = UNDEFINED_LINE_NUMBER
                    columnNumber = UNDEFINED_COLUMN_NUMBER
                }

                else -> {
                    lineNumber = currentFile.fileEntry.getLineNumber(effectiveStartOffset) + 1 // since humans count from 1, not 0
                    columnNumber = currentFile.fileEntry.getColumnNumber(effectiveStartOffset) + 1
                }
            }

            // TODO: should module name still be added here?
            return Location("$moduleName @ $filePath", lineNumber, columnNumber)
        }

        private tailrec fun IrElement.startOffsetOfFirstDenotableIrElement(): Int = when (this) {
            is IrPackageFragment -> UNDEFINED_OFFSET
            !is IrDeclaration -> {
                // We don't generate non-denotable IR expressions in the course of partial linkage.
                startOffset
            }

            else -> if (origin is PartiallyLinkedDeclarationOrigin) {
                // There is no sense to take coordinates from the declaration that does not exist in the code.
                // Let's take the coordinates of the parent.
                parent.startOffsetOfFirstDenotableIrElement()
            } else {
                startOffset
            }
        }

        @Suppress("ClassName")
        private object PARTIAL_LINKAGE_RUNTIME_ERROR : IrStatementOriginImpl("PARTIAL_LINKAGE_RUNTIME_ERROR")
    }
}
