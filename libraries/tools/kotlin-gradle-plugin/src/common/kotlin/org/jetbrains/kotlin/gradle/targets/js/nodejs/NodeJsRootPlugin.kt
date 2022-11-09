/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.nodejs

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.targets.js.MultiplePluginDeclarationDetector
import org.jetbrains.kotlin.gradle.targets.js.npm.CompositeNodeModulesCache
import org.jetbrains.kotlin.gradle.targets.js.npm.GradleNodeModulesCache
import org.jetbrains.kotlin.gradle.targets.js.npm.KotlinNpmResolutionManager
import org.jetbrains.kotlin.gradle.targets.js.npm.asNpmEnvironment
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinRootNpmResolverStateHolder
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.MayBeUpToDatePackageJsonTasksRegistry
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.PACKAGE_JSON_UMBRELLA_TASK_NAME
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmCachesSetup
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.tasks.CleanDataTask
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.utils.castIsolatedKotlinPluginClassLoaderAware

open class NodeJsRootPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        MultiplePluginDeclarationDetector.detect(project)

        project.plugins.apply(BasePlugin::class.java)

        check(project == project.rootProject) {
            "NodeJsRootPlugin can be applied only to root project"
        }

        val npmResolutionManagerStateHolder = project.gradle.sharedServices.registerIfAbsent(
            "npm-resolution-manager-state-holder", KotlinNpmResolutionManager.KotlinNpmResolutionManagerStateHolder::class.java
        ) {
        }

        val nodeJs = project.extensions.create(
            NodeJsRootExtension.EXTENSION_NAME,
            NodeJsRootExtension::class.java,
            project.logger,
            project.gradle.gradleUserHomeDir,
            project.projectDir,
            project.buildDir,
        )

        project.extensions.create(
            NodeJsTaskProviders.EXTENSION_NAME,
            NodeJsTaskProviders::class.java,
            project
        )

        val setupTask = project.registerTask<NodeJsSetupTask>(NodeJsSetupTask.NAME) {
            it.group = TASKS_GROUP_NAME
            it.description = "Download and install a local node/npm version"
            it.configuration = project.provider {
                project.configurations.detachedConfiguration(project.dependencies.create(it.ivyDependency))
                    .also { conf -> conf.isTransitive = false }
            }
        }

        val rootClean = project.rootProject.tasks.named(BasePlugin.CLEAN_TASK_NAME)

        val setupFileHasherTask = project.registerTask<KotlinNpmCachesSetup>(KotlinNpmCachesSetup.NAME) {
            it.description = "Setup file hasher for caches"
        }

        project.registerTask<KotlinNpmInstallTask>(KotlinNpmInstallTask.NAME) {
            it.dependsOn(setupTask)
            it.dependsOn(setupFileHasherTask)
            it.group = TASKS_GROUP_NAME
            it.description = "Find, download and link NPM dependencies and projects"

            it.mustRunAfter(rootClean)
        }

        project.registerTask<Task>(PACKAGE_JSON_UMBRELLA_TASK_NAME)

        val yarnExtension = YarnPlugin.apply(project)

        val yarnEnv = project.provider {
            yarnExtension.requireConfigured()
        }

        val npmEnvironment = project.provider {
            nodeJs.asNpmEnvironment
        }

        val yarnResolutions = project.provider {
            yarnExtension.resolutions
        }

        val resolverStateHolder = project.gradle.sharedServices.registerIfAbsent(
            KotlinRootNpmResolverStateHolder::class.qualifiedName,
            KotlinRootNpmResolverStateHolder::class.java
        ) { service ->
            service.parameters.projectResolvers.set(mutableMapOf())
            service.parameters.packageManager.set(nodeJs.packageManager)
            service.parameters.yarnEnvironment.set(yarnEnv)
            service.parameters.npmEnvironment.set(npmEnvironment)
            service.parameters.yarnResolutions.set(yarnResolutions)
            service.parameters.taskRequirements.set(nodeJs.taskRequirements)
        }

        val gradleNodeModulesProvider: Provider<GradleNodeModulesCache> =
            project.gradle.sharedServices.registerIfAbsent("gradle-node-modules", GradleNodeModulesCache::class.java) {
                it.parameters.cacheDir.set(nodeJs.nodeModulesGradleCacheDir)
                it.parameters.rootProjectDir.set(project.projectDir)
            }

        val compositeNodeModulesProvider: Provider<CompositeNodeModulesCache> =
            project.gradle.sharedServices.registerIfAbsent("composite-node-modules", CompositeNodeModulesCache::class.java) {
                it.parameters.cacheDir.set(nodeJs.nodeModulesGradleCacheDir)
                it.parameters.rootProjectDir.set(project.projectDir)
            }

        _kotlinNpmResolutionManager = KotlinNpmResolutionManager(
            nodeJs,
            npmResolutionManagerStateHolder,
            project.name,
            project.version.toString(),
            resolverStateHolder,
            gradleNodeModulesProvider,
            compositeNodeModulesProvider,
            MayBeUpToDatePackageJsonTasksRegistry.registerIfAbsent(project)
        )

        project.tasks.register("node" + CleanDataTask.NAME_SUFFIX, CleanDataTask::class.java) {
            it.cleanableStoreProvider = project.provider { nodeJs.requireConfigured().cleanableStore }
            it.group = TASKS_GROUP_NAME
            it.description = "Clean unused local node version"
        }
    }

    companion object {
        const val TASKS_GROUP_NAME: String = "nodeJs"

        fun apply(rootProject: Project): NodeJsRootExtension {
            check(rootProject == rootProject.rootProject)
            rootProject.plugins.apply(NodeJsRootPlugin::class.java)
            return rootProject.extensions.getByName(NodeJsRootExtension.EXTENSION_NAME) as NodeJsRootExtension
        }

        val Project.kotlinNodeJsExtension: NodeJsRootExtension
            get() = extensions.getByName(NodeJsRootExtension.EXTENSION_NAME).castIsolatedKotlinPluginClassLoaderAware()

        val Project.kotlinNodeJsTaskProvidersExtension: NodeJsTaskProviders
            get() = extensions.getByName(NodeJsTaskProviders.EXTENSION_NAME).castIsolatedKotlinPluginClassLoaderAware()

        var _kotlinNpmResolutionManager: KotlinNpmResolutionManager? = null
        val Project.kotlinNpmResolutionManager: KotlinNpmResolutionManager
            get() = _kotlinNpmResolutionManager!!
    }
}
