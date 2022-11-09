/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.nodejs

import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.internal.ConfigurationPhaseAware
import org.jetbrains.kotlin.gradle.logging.kotlinInfo
import org.jetbrains.kotlin.gradle.targets.js.NpmVersions
import org.jetbrains.kotlin.gradle.targets.js.npm.KotlinNpmResolutionManager
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmApi
import org.jetbrains.kotlin.gradle.targets.js.yarn.Yarn
import org.jetbrains.kotlin.gradle.tasks.internal.CleanableStore
import java.io.File
import java.io.Serializable

open class NodeJsRootExtension(
    private val logger: Logger,
    gradleUserHomeDir: File,
    val rootProjectDir: File,
    rootProjectBuildDir: File,
) : ConfigurationPhaseAware<NodeJsEnv>(), Serializable {

    private val gradleHome = gradleUserHomeDir.also {
        logger.kotlinInfo("Storing cached files in $it")
    }

    var installationDir by Property(gradleHome.resolve("nodejs"))

    var download by Property(true)

    var nodeDownloadBaseUrl by Property("https://nodejs.org/dist")
    var nodeVersion by Property("16.13.0")

    var nodeCommand by Property("node")

    var packageManager: NpmApi by Property(Yarn())

    val taskRequirements: TasksRequirements = TasksRequirements()

    val rootPackageDir: File = rootProjectBuildDir.resolve("js")

    val projectPackagesDir: File
        get() = rootPackageDir.resolve("packages")

    val nodeModulesGradleCacheDir: File
        get() = rootPackageDir.resolve("packages_imported")

    override fun finalizeConfiguration(): NodeJsEnv {
        val platform = NodeJsPlatform.name
        val architecture = NodeJsPlatform.architecture

        val nodeDirName = "node-v$nodeVersion-$platform-$architecture"
        val cleanableStore = CleanableStore[installationDir.absolutePath]
        val nodeDir = cleanableStore[nodeDirName].use()
        val isWindows = NodeJsPlatform.name == NodeJsPlatform.WIN
        val nodeBinDir = if (isWindows) nodeDir else nodeDir.resolve("bin")

        fun getExecutable(command: String, customCommand: String, windowsExtension: String): String {
            val finalCommand = if (isWindows && customCommand == command) "$command.$windowsExtension" else customCommand
            return if (download) File(nodeBinDir, finalCommand).absolutePath else finalCommand
        }

        fun getIvyDependency(): String {
            val type = if (isWindows) "zip" else "tar.gz"
            return "org.nodejs:node:$nodeVersion:$platform-$architecture@$type"
        }

        return NodeJsEnv(
            cleanableStore = cleanableStore,
            nodeDir = nodeDir,
            nodeBinDir = nodeBinDir,
            nodeExecutable = getExecutable("node", nodeCommand, "exe"),
            platformName = platform,
            architectureName = architecture,
            ivyDependency = getIvyDependency(),
            downloadBaseUrl = nodeDownloadBaseUrl
        )
    }

    val versions = NpmVersions()

//    val npmResolutionManager = KotlinNpmResolutionManager(this, npmResolutionManagerStateHolder)

    companion object {
        const val EXTENSION_NAME: String = "kotlinNodeJs"
    }
}
