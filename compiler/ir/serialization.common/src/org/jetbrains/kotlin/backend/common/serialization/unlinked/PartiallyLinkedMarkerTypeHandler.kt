/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.unlinked

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl

// TODO: Do we need it?
internal interface PartiallyLinkedMarkerTypeHandler {
    val markerType: IrType
    fun isMarkerType(type: IrType): Boolean
}

internal class PartiallyLinkedMarkerTypeHandlerImpl(builtIns: IrBuiltIns) : PartiallyLinkedMarkerTypeHandler {
    override val markerType = IrSimpleTypeImpl(
        classifier = builtIns.anyClass,
        hasQuestionMark = true,
        arguments = emptyList(),
        annotations = emptyList()
    )

    override fun isMarkerType(type: IrType): Boolean = type === markerType
}
