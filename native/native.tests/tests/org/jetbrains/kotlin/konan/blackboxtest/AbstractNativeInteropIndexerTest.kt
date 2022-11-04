/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.testFramework.TestDataFile
import org.jetbrains.kotlin.konan.blackboxtest.support.*
import org.jetbrains.kotlin.konan.blackboxtest.support.compilation.*
import org.jetbrains.kotlin.konan.blackboxtest.support.compilation.TestCompilationArtifact.*
import org.jetbrains.kotlin.konan.blackboxtest.support.runner.*
import org.jetbrains.kotlin.konan.blackboxtest.support.settings.*
import org.jetbrains.kotlin.konan.blackboxtest.support.util.*
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import java.io.File

abstract class AbstractNativeInteropIndexerFModulesTest : AbstractNativeInteropIndexerTest() {
    override val fmodules = true
}

abstract class AbstractNativeInteropIndexerNoFModulesTest : AbstractNativeInteropIndexerTest() {
    override val fmodules = false
}

@Tag("interop-indexer")
abstract class AbstractNativeInteropIndexerTest : AbstractNativeInteropIndexerBaseTest() {
    abstract val fmodules: Boolean

    @Synchronized
    protected fun runTest(@TestDataFile testPath: String) {
        // FIXME: check the following failures under Android with -fmodules
        // fatal error: could not build module 'std'
        Assumptions.assumeFalse(
            this is AbstractNativeInteropIndexerFModulesTest &&
                    targets.testTarget.family == Family.ANDROID
        )
        // FIXME: KT-54759 remove the following assumption and fix cinterop to pass those tests for Apple targets.
        // Now `clang -fmodules` cannot compile cstubs.c using included Darwin module from sysroot
        Assumptions.assumeFalse(
            this is AbstractNativeInteropIndexerFModulesTest &&
                    targets.testTarget.family.isAppleFamily &&
                    (testPath.endsWith("/builtinsDefs/fullStdargH/") || testPath.endsWith("/builtinsDefs/fullA/"))
        )

        val testPathFull = getAbsoluteFile(testPath)
        val testDataDir = testPathFull.parentFile.parentFile
        val includeFolder = testDataDir.resolve("include")
        val defFile = testPathFull.resolve("pod1.def")
        val defHasHeaders = defFile.readText().split("\n").any { it.startsWith("headers") }
        Assumptions.assumeFalse(fmodules && defHasHeaders)

        val goldenFile = if (testDataDir.name == "builtins")
            getBuiltinsGoldenFile(testPathFull)
        else
            getGoldenFile(testPathFull)
        val fmodulesArgs = if (fmodules) listOf("-compiler-option", "-fmodules") else listOf()
        val includeArgs = if (testDataDir.name.startsWith("framework"))
            listOf("-compiler-option", "-F${testDataDir.canonicalPath}")
        else
            listOf("-compiler-option", "-I${includeFolder.canonicalPath}")

        val testCase: TestCase = generateCInteropTestCaseWithSingleDef(defFile, includeArgs + fmodulesArgs)
        val testCompilationResult = testCase.cinteropToLibrary()
        val klibContents = testCompilationResult.resultingArtifact.getContents()

        val expectedContents = goldenFile.readText()
        assertEquals(StringUtilRt.convertLineSeparators(expectedContents), StringUtilRt.convertLineSeparators(klibContents)) {
            "Test failed. CInterop compilation result was: $testCompilationResult"
        }
    }

    private fun getGoldenFile(testPathFull: File): File {
        return testPathFull.resolve("contents.gold.txt")
    }

    private fun getBuiltinsGoldenFile(testPathFull: File): File {
        val goldenFilePart = when (targets.testTarget) {
            KonanTarget.ANDROID_ARM32 -> "ARM32"
            KonanTarget.ANDROID_ARM64 -> "ARM64"
            KonanTarget.ANDROID_X64 -> "X64"
            KonanTarget.ANDROID_X86 -> "CPointerByteVar"
            KonanTarget.IOS_ARM32 -> "COpaquePointer"
            KonanTarget.IOS_ARM64 -> "CPointerByteVar"
            KonanTarget.IOS_SIMULATOR_ARM64 -> "CPointerByteVar"
            KonanTarget.IOS_X64 -> "X64"
            KonanTarget.LINUX_ARM32_HFP -> "ARM32"
            KonanTarget.LINUX_ARM64 -> "ARM64"
            KonanTarget.LINUX_MIPS32 -> "COpaquePointer"
            KonanTarget.LINUX_MIPSEL32 -> "COpaquePointer"
            KonanTarget.LINUX_X64 -> "X64"
            KonanTarget.MACOS_ARM64 -> "CPointerByteVar"
            KonanTarget.MACOS_X64 -> "X64"
            KonanTarget.MINGW_X64 -> "CPointerByteVar"
            KonanTarget.MINGW_X86 -> "CPointerByteVar"
            KonanTarget.TVOS_ARM64 -> "CPointerByteVar"
            KonanTarget.TVOS_SIMULATOR_ARM64 -> "CPointerByteVar"
            KonanTarget.TVOS_X64 -> "X64"
            KonanTarget.WASM32 -> "COpaquePointer"
            KonanTarget.WATCHOS_ARM32 -> "CPointerByteVar"
            KonanTarget.WATCHOS_ARM64 -> "CPointerByteVar"
            KonanTarget.WATCHOS_DEVICE_ARM64 -> "CPointerByteVar"
            KonanTarget.WATCHOS_SIMULATOR_ARM64 -> "CPointerByteVar"
            KonanTarget.WATCHOS_X64 -> "X64"
            KonanTarget.WATCHOS_X86 -> "CPointerByteVar"
            is KonanTarget.ZEPHYR -> "COpaquePointer"
        }
        return testPathFull.resolve("contents.gold.${goldenFilePart}.txt")
    }
}
