/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.lower.loops.forLoopsPhase
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.functions

val jvmExposePhase = makeIrFilePhase(
    ::JvmExposeDeclarationsLowering,
    name = "Inline Classes",
    description = "Lower inline classes",
    prerequisite = setOf(
        jvmInlineClassPhase,
    ),
)

private class JvmExposeDeclarationsLowering(private val context: JvmBackendContext) : ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        for (function in irClass.functions) {

        }
    }
}
