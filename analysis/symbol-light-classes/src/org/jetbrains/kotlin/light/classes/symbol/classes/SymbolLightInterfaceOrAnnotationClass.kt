/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.classes

import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiReferenceList
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.light.classes.symbol.NullabilityType
import org.jetbrains.kotlin.light.classes.symbol.annotations.computeAnnotations
import org.jetbrains.kotlin.light.classes.symbol.modifierLists.SymbolLightClassModifierList
import org.jetbrains.kotlin.light.classes.symbol.toPsiVisibilityForClass
import org.jetbrains.kotlin.psi.KtClassOrObject

internal abstract class SymbolLightInterfaceOrAnnotationClass(classOrObject: KtClassOrObject, ktModule: KtModule) :
    SymbolLightClassForClassOrObject(classOrObject, ktModule) {

    init {
        require(isInterface || isAnnotation)
    }

    private val _modifierList: PsiModifierList? by lazyPub {
        withNamedClassOrObjectSymbol { classOrObjectSymbol ->
            val modifiers = mutableSetOf(classOrObjectSymbol.toPsiVisibilityForClass(isNested = !isTopLevel), PsiModifier.ABSTRACT)
            if (!isTopLevel && !classOrObjectSymbol.isInner) {
                modifiers.add(PsiModifier.STATIC)
            }

            val annotations = classOrObjectSymbol.computeAnnotations(
                parent = this@SymbolLightInterfaceOrAnnotationClass,
                nullability = NullabilityType.Unknown,
                annotationUseSiteTarget = null,
            )

            SymbolLightClassModifierList(this@SymbolLightInterfaceOrAnnotationClass, modifiers, annotations)
        }
    }

    override fun isInterface(): Boolean = true
    override fun isEnum(): Boolean = false

    override fun getModifierList(): PsiModifierList? = _modifierList

    override fun getImplementsList(): PsiReferenceList? = null
}
