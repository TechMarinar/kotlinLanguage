/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package kotlin.native.internal

import kotlin.coroutines.Continuation
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.WorkerBoundReference
import kotlin.native.concurrent.ThrowWorkerAlreadyTerminated
import kotlin.native.ref.getWeakReferenceImpl
import kotlinx.cinterop.ForeignObjCObject

/**
 * References all the files referenced by native runtime
 * (containing either ExportTypeInfo or ExportForCppRuntime annotations).
 */
@OptIn(FreezingIsDeprecated::class)
internal fun touchDependencies(x: Int, y: NativePtr, continuation: Continuation<Any?>): Int {
    var hash = 0
    hash += Any().hashCode() // Any.kt
    hash += Unit.hashCode() // Unit.kt
    hash += String().hashCode() // String.kt
    hash += Array<Any>(1).hashCode() // Array.kt
    hash += ByteArray(1).hashCode() // Arrays.kt
    hash += Throwable().hashCode() // Throwable.kt
    hash += getNativeNullPtr().hashCode() // NativePtr.kt
    hash += AtomicInt(42).hashCode() // Atomics.kt
    hash += WorkerBoundReference(Any()).hashCode() // WorkerBoundReference.kt
    hash += ForeignObjCObject().hashCode() // ObjectiveCImpl.kt
    hash += NSArrayAsKList().hashCode() // ObjCExportUtils.kt
    performGCOnCleanerWorker() // Cleaner.kt
    checkRangeIndexes(0, 0, 0) // ArrayUtil.kt
    if (x == 0) ThrowNullPointerException() // RuntimeUtils.kt
    if (x == 1) ThrowWorkerAlreadyTerminated() // Internal.kt
    hash += getWeakReferenceImpl(Any()).hashCode() // WeakPrivate.kt
    resumeContinuation(continuation, null) // ObjCExportCoroutines.kt
    hash += DescribeObjectForDebugging(y, y).hashCode() // Utils.kt

    return hash
}
