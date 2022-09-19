/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.jvm.checkers.declaration

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.analysis.diagnostics.jvm.FirJvmErrors
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isOpen
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirConstExpression
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

object FirJvmNameAndExposeChecker : FirBasicDeclarationChecker() {
    override fun check(declaration: FirDeclaration, context: CheckerContext, reporter: DiagnosticReporter) {
        var jvmName = declaration.findAnnotation(StandardClassIds.Annotations.JvmName)
        val jvmExpose = declaration.findAnnotation(StandardClassIds.Annotations.JvmExpose)

        if (jvmName != null && jvmExpose != null) {
            reporter.reportOn(jvmName.source, FirJvmErrors.CONFLICTING_JVM_NAME_AND_EXPOSE, context)
        }

        jvmName?.let { context.checkJvmName(it, declaration, reporter) }
        jvmExpose?.let { context.checkJvmExpose(it, declaration, reporter) }
    }

    fun CheckerContext.checkJvmName(jvmName: FirAnnotation, declaration: FirDeclaration, reporter: DiagnosticReporter) {
        val nameParameter = jvmName.findArgumentByName(StandardClassIds.Annotations.ParameterNames.jvmNameName) ?: return

        if (nameParameter.typeRef.coneType != session.builtinTypes.stringType.type) {
            return
        }

        val value = (nameParameter as? FirConstExpression<*>)?.value as? String ?: return

        if (!Name.isValidIdentifier(value)) {
            reporter.reportOn(jvmName.source, FirJvmErrors.ILLEGAL_JVM_NAME, this)
        }

        if (declaration is FirFunction && !isRenamableFunction(declaration)) {
            reporter.reportOn(jvmName.source, FirJvmErrors.INAPPLICABLE_JVM_NAME, this)
        } else if (declaration is FirCallableDeclaration) {
            val containingClass = declaration.getContainingClass(session)

            if (
                declaration.isOverride ||
                declaration.isOpen ||
                containingClass?.isInlineThatRequiresMangling() == true
            ) {
                reporter.reportOn(jvmName.source, FirJvmErrors.INAPPLICABLE_JVM_NAME, this)
            }
        }
    }

    fun CheckerContext.checkJvmExpose(jvmExpose: FirAnnotation, declaration: FirDeclaration, reporter: DiagnosticReporter) {
        val nameParameter = jvmExpose.findArgumentByName(StandardClassIds.Annotations.ParameterNames.jvmNameName) ?: return

        if (nameParameter.typeRef.coneType != session.builtinTypes.stringType.type) {
            return
        }

        val value = (nameParameter as? FirConstExpression<*>)?.value as? String ?: return

        if (value != "" && !Name.isValidIdentifier(value)) {
            reporter.reportOn(jvmExpose.source, FirJvmErrors.ILLEGAL_JVM_NAME, this)
        }

        when {
            declaration is FirMemberDeclaration && Visibilities.isPrivate(declaration.visibility) ||
                    declaration.findAnnotation(StandardClassIds.Annotations.JvmSynthetic) != null -> {
                reporter.reportOn(jvmExpose.source, FirJvmErrors.INAPPLICABLE_JVM_EXPOSE, this)
            }

            declaration is FirFunction && !isRenamableFunction(declaration) -> {
                reporter.reportOn(jvmExpose.source, FirJvmErrors.INAPPLICABLE_JVM_EXPOSE, this)
            }

            declaration is FirCallableDeclaration -> {
                val containingClass = declaration.getContainingClass(session)

                if (
                    declaration.isOverride ||
                    declaration.isOpen ||
                    containingClass?.isInlineThatRequiresMangling() == true
                ) {
                    reporter.reportOn(jvmExpose.source, FirJvmErrors.INAPPLICABLE_JVM_EXPOSE, this)
                }
            }
        }
    }

    private fun FirDeclaration.findAnnotation(id: ClassId): FirAnnotation? = annotations.find {
        it.annotationTypeRef.coneType.classId == id
    }

    /**
     * Local functions can't be renamed as well as functions not present in any class, like intrinsics.
     */
    private fun CheckerContext.isRenamableFunction(function: FirFunction): Boolean {
        val containingClass = function.getContainingClassSymbol(session)
        return containingClass != null || !function.symbol.callableId.isLocal
    }

    private fun FirRegularClass.isInlineThatRequiresMangling(): Boolean = isInline && name == StandardClassIds.Result.shortClassName
}
