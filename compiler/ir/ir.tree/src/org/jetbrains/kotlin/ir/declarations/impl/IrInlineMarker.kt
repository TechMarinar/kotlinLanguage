/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations.impl

import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrInlineMarker
import org.jetbrains.kotlin.ir.expressions.IrCall

class IrInlineMarkerImpl(
    override val startOffset: Int,
    override val endOffset: Int,
    override val inlineCall: IrCall,
    override val callee: IrFunction,
    override val isInlineOnLambda: Boolean,
) : IrInlineMarker()