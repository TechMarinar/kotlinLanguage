/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.unlinked

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol

/**
 * Describes a reason why an IR declaration could be partially linked. Subclasses represent various causes of p.l.
 */
internal sealed interface PartialLinkageCase {
    /**
     * There is no real owner declaration for the symbol, only synthetic stub created by [MissingDeclarationStubGenerator].
     * Likely the declaration has been deleted in newer version of the library.
     */
    class MissingDeclaration(val symbol: IrSymbol) : PartialLinkageCase

    /**
     * There is no enclosing class for inner class (or enum entry). This might happen if the inner class became a top-level class
     * in newer version of the library.
     */
    class MissingEnclosingClass(val clazz: IrClass) : PartialLinkageCase

    /**
     * Declaration's signature uses partially linked symbol.
     */
    class DeclarationUsesPartiallyLinkedSymbol(
        val declaration: IrDeclaration,
        val cause: LinkedClassifierStatus.Partially
    ) : PartialLinkageCase

    /**
     * Expression references a missing IR declaration (IR declaration != classifier).
     */
    class ExpressionUsesMissingDeclaration(
        val expression: IrExpression,
        val missingDeclarationSymbol: IrSymbol
    ) : PartialLinkageCase

    /**
     * Expression operates on IR type which has partially linked symbol.
     */
    class ExpressionUsesPartiallyLinkedClassifier(
        val expression: IrExpression,
        val cause: LinkedClassifierStatus.Partially
    ) : PartialLinkageCase

    /** Unimplemented abstract callable member in non-abstract class. */
    class UnimplementedAbstractCallable(val callable: IrOverridableDeclaration<*>) : PartialLinkageCase
}
