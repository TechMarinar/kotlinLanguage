/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.SymbolTable

object IrActualizer {
    fun actualize(platformFragment: IrModuleFragment, platformSymbolTable: SymbolTable, commonFragments: List<IrModuleFragment>) {
        linkExpectToActual(platformSymbolTable, commonFragments)
        mergeIrFragments(platformFragment, commonFragments)
    }

    private fun linkExpectToActual(platformSymbolTable: SymbolTable, commonFragments: List<IrModuleFragment>) {
        val actualizer = IrActualizerTransformer(platformSymbolTable)
        for (commonFragment in commonFragments) {
            commonFragment.transform(actualizer, null)
        }
    }

    private fun mergeIrFragments(platformFragment: IrModuleFragment, commonFragments: List<IrModuleFragment>) {
        platformFragment.files.addAll(commonFragments.flatMap { it.files })
    }
}