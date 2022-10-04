/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.methods

import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiParameterList
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.utils.errors.requireIsInstance
import org.jetbrains.kotlin.asJava.builder.LightMemberOrigin
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.light.classes.symbol.SymbolLightIdentifier
import org.jetbrains.kotlin.light.classes.symbol.annotations.hasDeprecatedAnnotation
import org.jetbrains.kotlin.light.classes.symbol.classes.SymbolLightClassBase
import org.jetbrains.kotlin.light.classes.symbol.classes.analyzeForLightClasses
import org.jetbrains.kotlin.light.classes.symbol.getOrRestoreSymbol
import org.jetbrains.kotlin.light.classes.symbol.parameters.SymbolLightParameter
import org.jetbrains.kotlin.light.classes.symbol.parameters.SymbolLightParameterList
import org.jetbrains.kotlin.light.classes.symbol.parameters.SymbolLightSuspendContinuationParameter
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import java.util.*

internal abstract class SymbolLightMethod(
    functionSymbol: KtFunctionLikeSymbol,
    lightMemberOrigin: LightMemberOrigin?,
    containingClass: SymbolLightClassBase,
    methodIndex: Int,
    argumentsSkipMask: BitSet? = null
) : SymbolLightMethodBase(
    lightMemberOrigin,
    containingClass,
    methodIndex
) {
    private val ktModule: KtModule = containingClass.ktModule

    protected val functionDeclaration: KtFunction? = functionSymbol.psi as? KtFunction
    protected val functionSymbolPointer: KtSymbolPointer<KtFunctionLikeSymbol> = functionSymbol.createPointer()
    protected fun <T> withFunctionSymbol(action: KtAnalysisSession.(KtFunctionLikeSymbol) -> T): T = analyzeForLightClasses(ktModule) {
        action(getOrRestoreSymbol(functionDeclaration, functionSymbolPointer))
    }

    private val _isVarArgs: Boolean by lazyPub {
        functionDeclaration?.valueParameters?.any { it.isVarArg } ?: withFunctionSymbol { functionSymbol ->
            functionSymbol.valueParameters.any { it.isVararg }
        }
    }

    override fun isVarArgs(): Boolean = _isVarArgs

    private val _parametersList by lazyPub {
        SymbolLightParameterList(this@SymbolLightMethod, functionSymbolPointer, ktModule) { builder, functionSymbol ->
            requireIsInstance<KtFunctionLikeSymbol>(functionSymbol)

            functionSymbol.valueParameters.mapIndexed { index, parameter ->
                val needToSkip = argumentsSkipMask?.get(index) == true
                if (!needToSkip) {
                    builder.addParameter(
                        SymbolLightParameter(
                            parameterSymbol = parameter,
                            containingMethod = this@SymbolLightMethod
                        )
                    )
                }
            }

            if ((functionSymbol as? KtFunctionSymbol)?.isSuspend == true) {
                builder.addParameter(
                    SymbolLightSuspendContinuationParameter(
                        functionSymbol = functionSymbol,
                        containingMethod = this@SymbolLightMethod
                    )
                )
            }
        }
    }

    private val _isDeprecated: Boolean by lazyPub {
        withFunctionSymbol { functionSymbol ->
            functionSymbol.hasDeprecatedAnnotation()
        }
    }

    override fun isDeprecated(): Boolean = _isDeprecated

    private val _identifier: PsiIdentifier by lazyPub {
        withFunctionSymbol { functionSymbol ->
            SymbolLightIdentifier(this@SymbolLightMethod, functionSymbol)
        }
    }

    override fun getNameIdentifier(): PsiIdentifier = _identifier

    override fun getParameterList(): PsiParameterList = _parametersList

    override val kotlinOrigin: KtDeclaration? = functionDeclaration ?: lightMemberOrigin?.originalElement

    override fun isValid(): Boolean = super.isValid() && functionDeclaration?.isValid ?: analyzeForLightClasses(ktModule) {
        functionSymbolPointer.restoreSymbol() != null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SymbolLightMethod) return false
        if (functionDeclaration != null) {
            return functionDeclaration == other.functionDeclaration
        }

        return other.functionDeclaration == null &&
                methodIndex == other.methodIndex &&
                ktModule == other.ktModule &&
                name == other.name &&
                containingClass == other.containingClass &&
                analyzeForLightClasses(ktModule) {
                    functionSymbolPointer.restoreSymbol() == other.functionSymbolPointer.restoreSymbol()
                }
    }

    override fun hashCode(): Int = kotlinOrigin.hashCode()

    override fun isOverride(): Boolean = withFunctionSymbol { it.getDirectlyOverriddenSymbols().isNotEmpty() }
}
