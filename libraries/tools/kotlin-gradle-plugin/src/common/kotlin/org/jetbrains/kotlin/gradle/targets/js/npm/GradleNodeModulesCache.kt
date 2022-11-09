/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.jetbrains.kotlin.gradle.utils.ArchiveOperationsCompat
import org.jetbrains.kotlin.gradle.utils.FileSystemOperationsCompat
import java.io.File
import javax.inject.Inject

/**
 * Cache for storing already created [GradleNodeModule]s
 */
internal abstract class GradleNodeModulesCache : AbstractNodeModulesCache() {

    @get:Inject
    abstract val fs: FileSystemOperations

    override val type: String
        get() = "gradle"

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    override fun buildImportedPackage(
        name: String,
        version: String,
        file: File
    ): File? {
        val module = GradleNodeModuleBuilder(fs, archiveOperations, name, version, listOf(file), parameters.cacheDir.get().asFile)
        module.visitArtifacts()
        return module.rebuild()
    }
}