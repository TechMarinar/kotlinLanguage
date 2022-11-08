/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.unlinked

import org.jetbrains.kotlin.backend.common.serialization.unlinked.DeclarationKind.*
import org.jetbrains.kotlin.backend.common.serialization.unlinked.ExpressionKind.*
import org.jetbrains.kotlin.backend.common.serialization.unlinked.LinkedClassifierStatus.Partially
import org.jetbrains.kotlin.backend.common.serialization.unlinked.PartialLinkageCase.*
import org.jetbrains.kotlin.backend.common.serialization.unlinked.PartialLinkageUtils.UNKNOWN_NAME
import org.jetbrains.kotlin.backend.common.serialization.unlinked.PartialLinkageUtils.guessName
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.ir.util.nameForIrSerialization
import org.jetbrains.kotlin.ir.util.parentAsClass

// TODO: Simplify and enhance PL error messages when new self-descriptive signatures are implemented.
internal fun PartialLinkageCase.renderErrorMessage(): String = buildString {
    when (this@renderErrorMessage) {
        is MissingDeclaration -> append("No ").append(symbol.declarationKind).append(" found for signature ").appendSignature(symbol)

        is MissingEnclosingClass -> appendDeclaration(clazz).append(" is expected to have enclosing class which is missing")

        is DeclarationUsesPartiallyLinkedClassifier -> append("The signature of ").appendDeclaration(declaration)
            .append(" uses ").appendCause(cause)

        is ExpressionUsesMissingDeclaration -> appendExpression(expression).append(" because it uses unlinked symbol ")
            .appendSignature(missingDeclarationSymbol)

        is ExpressionUsesPartiallyLinkedClassifier -> appendExpression(expression).append(" because it uses ").appendCause(cause)

        is UnimplementedAbstractCallable -> append("Abstract ").appendDeclaration(callable)
            .append(" is not implemented in non-abstract ").appendDeclaration(callable.parentAsClass)
    }
}

private enum class DeclarationKind(private val displayName: String) {
    CLASS("class"),
    INTERFACE("interface"),
    ENUM_CLASS("enum class"),
    ENUM_ENTRY("enum entry"),
    ANNOTATION_CLASS("annotation class"),
    OBJECT("object"),
    ANONYMOUS_OBJECT("anonymous object"),
    COMPANION_OBJECT("companion object"),
    MUTABLE_VARIABLE("var"),
    IMMUTABLE_VARIABLE("val"),
    VALUE_PARAMETER("value parameter"),
    FIELD("field"),
    FIELD_OF_PROPERTY("backing field of property"),
    PROPERTY("property"),
    PROPERTY_ACCESSOR("property accessor"),
    FUNCTION("function"),
    CONSTRUCTOR("constructor"),
    OTHER_DECLARATION("declaration");

    override fun toString() = displayName
}

private val IrSymbol.declarationKind: DeclarationKind
    get() = when (this) {
        is IrClassSymbol -> when (owner.kind) {
            ClassKind.CLASS -> if (owner.isAnonymousObject) ANONYMOUS_OBJECT else CLASS
            ClassKind.INTERFACE -> INTERFACE
            ClassKind.ENUM_CLASS -> ENUM_CLASS
            ClassKind.ENUM_ENTRY -> ENUM_ENTRY
            ClassKind.ANNOTATION_CLASS -> ANNOTATION_CLASS
            ClassKind.OBJECT -> if (owner.isCompanion) COMPANION_OBJECT else OBJECT
        }
        is IrEnumEntrySymbol -> ENUM_ENTRY
        is IrVariableSymbol -> if (owner.isVar) MUTABLE_VARIABLE else IMMUTABLE_VARIABLE
        is IrValueParameterSymbol -> VALUE_PARAMETER
        is IrFieldSymbol -> if (owner.correspondingPropertySymbol != null) FIELD_OF_PROPERTY else FIELD
        is IrPropertySymbol -> PROPERTY
        is IrSimpleFunctionSymbol -> if (owner.correspondingPropertySymbol != null) PROPERTY_ACCESSOR else FUNCTION
        is IrConstructorSymbol -> CONSTRUCTOR
        else -> OTHER_DECLARATION
    }

private data class Expression(val kind: ExpressionKind, val referencedDeclarationKind: DeclarationKind?)

private enum class ExpressionKind(val displayName: String?, val verb3rdForm: String) {
    REFERENCE("reference to", "evaluated"),
    CALLING(null, "called"),
    CALLING_INSTANCE_INITIALIZER("instance initializer of", "called"),
    READING(null, "read"),
    WRITING(null, "written"),
    GETTING_INSTANCE(null, "gotten"),
    OTHER_EXPRESSION("expression", "evaluated")
}

// More can be added for verbosity in the future.
private val IrExpression.expression: Expression
    get() = when (this) {
        is IrDeclarationReference -> when (this) {
            is IrFunctionReference -> Expression(REFERENCE, symbol.declarationKind)
            is IrPropertyReference,
            is IrLocalDelegatedPropertyReference -> Expression(REFERENCE, PROPERTY)
            is IrCall -> Expression(CALLING, symbol.declarationKind)
            is IrConstructorCall,
            is IrEnumConstructorCall,
            is IrDelegatingConstructorCall -> Expression(CALLING, CONSTRUCTOR)
            is IrClassReference -> Expression(REFERENCE, symbol.declarationKind)
            is IrGetField -> Expression(READING, symbol.declarationKind)
            is IrSetField -> Expression(WRITING, symbol.declarationKind)
            is IrGetValue -> Expression(READING, symbol.declarationKind)
            is IrSetValue -> Expression(WRITING, symbol.declarationKind)
            is IrGetSingletonValue -> Expression(GETTING_INSTANCE, symbol.declarationKind)
            else -> Expression(REFERENCE, OTHER_DECLARATION)
        }
        is IrInstanceInitializerCall -> Expression(CALLING_INSTANCE_INITIALIZER, classSymbol.declarationKind)
        else -> Expression(OTHER_EXPRESSION, null)
    }

private fun IrSymbol.guessName(): String? {
    return anySignature
        ?.let { effectiveSignature ->
            val nameSegmentsToPickUp = when {
                effectiveSignature is IdSignature.AccessorSignature -> 2 // property_name.accessor_name
                this is IrConstructorSymbol -> 2 // class_name.<init>
                else -> 1
            }
            effectiveSignature.guessName(nameSegmentsToPickUp)
        }
        ?: (owner as? IrDeclaration)?.nameForIrSerialization?.asString()
}

private val IrSymbol.anySignature: IdSignature?
    get() = signature ?: privateSignature

private const val UNKNOWN_SYMBOL = "<unknown symbol>"

private fun StringBuilder.appendSignature(symbol: IrSymbol): StringBuilder =
    append(symbol.anySignature?.render() ?: UNKNOWN_SYMBOL)

private fun StringBuilder.appendDeclaration(declaration: IrDeclaration): StringBuilder =
    appendDeclaration(declaration.symbol)

private fun StringBuilder.appendDeclaration(symbol: IrSymbol): StringBuilder {
    val declarationKind = symbol.declarationKind
    append(declarationKind)

    if (declarationKind != ANONYMOUS_OBJECT) {
        // This is a declaration NOT under a property.
        append(" ").append(symbol.guessName() ?: UNKNOWN_NAME.asString())
    }

    return this
}

private fun StringBuilder.appendExpression(expression: IrExpression): StringBuilder {
    val (expressionKind, referencedDeclarationKind) = expression.expression
    append(expressionKind.displayName)

    if (referencedDeclarationKind != null) {
        if (isNotEmpty()) append(" ")

        when (expression) {
            is IrDeclarationReference -> appendDeclaration(expression.symbol)
            is IrInstanceInitializerCall -> appendDeclaration(expression.classSymbol)
            else -> append(referencedDeclarationKind)
        }
    }

    return append(" can not be ").append(expressionKind.verb3rdForm)
}

private fun StringBuilder.appendCause(cause: Partially): StringBuilder =
    when (cause) {
        is Partially.MissingClassifier -> append("unlinked symbol ").appendSignature(cause.symbol)

        is Partially.MissingEnclosingClass -> appendDeclaration(cause.symbol).append(" without expected enclosing class")

        is Partially.DueToOtherClassifier -> append("partially linked symbol ").appendSignature(cause.symbol)
            .append(" (the root cause is ").appendCause(cause.rootCause).append(")")
    }
