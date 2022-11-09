/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.frontend.fir

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.jvm.compiler.PsiBasedProjectFileSearchScope
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.compiler.VfsBasedProjectEnvironment
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.checkers.registerExtendedCommonCheckers
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.session.FirCommonSessionFactory
import org.jetbrains.kotlin.fir.session.FirJvmSessionFactory
import org.jetbrains.kotlin.fir.session.FirSessionConfigurator
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.FrontendFacade
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.*

class FirFrontendFacade(
    testServices: TestServices,
    private val additionalSessionConfiguration: SessionConfiguration?
) : FrontendFacade<FirOutputArtifact>(testServices, FrontendKinds.FIR) {
    // Separate constructor is needed for creating callable references to it
    constructor(testServices: TestServices) : this(testServices, additionalSessionConfiguration = null)

    fun interface SessionConfiguration : (FirSessionConfigurator) -> Unit

    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::FirModuleInfoProvider))

    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(FirDiagnosticsDirectives)

    override fun analyze(module: TestModule): FirOutputArtifact {
        val compilerConfigurationProvider = testServices.compilerConfigurationProvider
        // TODO: add configurable parser

        val project = compilerConfigurationProvider.getProject(module)

        PsiElementFinder.EP.getPoint(project).unregisterExtension(JavaElementFinder::class.java)

        val lightTreeEnabled = FirDiagnosticsDirectives.USE_LIGHT_TREE in module.directives
        val (ktFiles, lightTreeFiles) = if (lightTreeEnabled) {
            emptyList<KtFile>() to testServices.sourceFileProvider.getLightTreeFilesForSourceFiles(module.files).values
        } else {
            testServices.sourceFileProvider.getKtFilesForSourceFiles(module.files, project).values to emptyList()
        }

        val moduleName = Name.identifier(module.name)
        val languageVersionSettings = module.languageVersionSettings
        val configuration = compilerConfigurationProvider.getCompilerConfiguration(module)
        val extensionRegistrars = FirExtensionRegistrar.getInstances(project)

        val sessionConfigurator: FirSessionConfigurator.() -> Unit = {
            if (FirDiagnosticsDirectives.WITH_EXTENDED_CHECKERS in module.directives) {
                registerExtendedCommonCheckers()
            }
            additionalSessionConfiguration?.invoke(this)
        }

        val dependencyConfigurator = when {
            module.targetPlatform.isCommon() || module.targetPlatform.isJvm() || module.targetPlatform.isNative() ->
                JvmDependenciesConfigurator(module, testServices, configuration)
            module.targetPlatform.isJs() -> JsDependenciesConfigurator(module, testServices)
            else -> error("Unsupported")
        }
        val dependencyList = dependencyConfigurator.buildDependencyList()
        val moduleInfoProvider = dependencyConfigurator.moduleInfoProvider

        val projectEnvironment: VfsBasedProjectEnvironment?

        when {
            module.targetPlatform.isCommon() || module.targetPlatform.isJvm() -> {
                val packagePartProviderFactory = compilerConfigurationProvider.getPackagePartProviderFactory(module)
                projectEnvironment = VfsBasedProjectEnvironment(
                    project, VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL),
                ) { packagePartProviderFactory.invoke(it) }
                val projectFileSearchScope = PsiBasedProjectFileSearchScope(ProjectScope.getLibrariesScope(project))
                val packagePartProvider = projectEnvironment.getPackagePartProvider(projectFileSearchScope)

                if (module.targetPlatform.isCommon()) {
                    FirCommonSessionFactory.createLibrarySession(
                        moduleName,
                        moduleInfoProvider.firSessionProvider,
                        dependencyList,
                        projectEnvironment,
                        projectFileSearchScope,
                        packagePartProvider,
                        languageVersionSettings
                    )
                } else {
                    FirJvmSessionFactory.createLibrarySession(
                        moduleName,
                        moduleInfoProvider.firSessionProvider,
                        dependencyList,
                        projectEnvironment,
                        projectFileSearchScope,
                        packagePartProvider,
                        languageVersionSettings
                    )
                }
            }
            module.targetPlatform.isJs() -> {
                projectEnvironment = null
                FirJsSessionFactory.createLibrarySession(
                    moduleName,
                    moduleInfoProvider.firSessionProvider,
                    dependencyList,
                    module,
                    testServices,
                    configuration,
                    languageVersionSettings,
                )
            }
            module.targetPlatform.isNative() -> {
                projectEnvironment = null
                FirNativeSessionFactory.createLibrarySession(
                    moduleName,
                    moduleInfoProvider.firSessionProvider,
                    dependencyList,
                    languageVersionSettings,
                )
            }
            else -> error("Unsupported")
        }

        val mainModuleData = FirModuleDataImpl(
            moduleName,
            dependencyList.regularDependencies,
            dependencyList.dependsOnDependencies,
            dependencyList.friendsDependencies,
            dependencyList.platform,
            dependencyList.analyzerServices
        )

        val session = when {
            module.targetPlatform.isCommon() -> {
                FirCommonSessionFactory.createModuleBasedSession(
                    mainModuleData,
                    moduleInfoProvider.firSessionProvider,
                    projectEnvironment!!,
                    incrementalCompilationContext = null,
                    extensionRegistrars,
                    languageVersionSettings,
                    lookupTracker = null,
                    enumWhenTracker = null,
                    sessionConfigurator,
                )
            }
            module.targetPlatform.isJvm() -> {
                FirJvmSessionFactory.createModuleBasedSession(
                    mainModuleData,
                    moduleInfoProvider.firSessionProvider,
                    PsiBasedProjectFileSearchScope(TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, ktFiles)),
                    projectEnvironment!!,
                    incrementalCompilationContext = null,
                    extensionRegistrars,
                    languageVersionSettings,
                    lookupTracker = null,
                    enumWhenTracker = null,
                    needRegisterJavaElementFinder = true,
                    init = sessionConfigurator,
                )
            }
            module.targetPlatform.isJs() -> {
                FirJsSessionFactory.createModuleBasedSession(
                    mainModuleData,
                    moduleInfoProvider.firSessionProvider,
                    extensionRegistrars,
                    languageVersionSettings,
                    null,
                    sessionConfigurator,
                )
            }
            module.targetPlatform.isNative() -> {
                FirNativeSessionFactory.createModuleBasedSession(
                    mainModuleData,
                    moduleInfoProvider.firSessionProvider,
                    extensionRegistrars,
                    languageVersionSettings,
                    init = sessionConfigurator
                )
            }
            else -> error("Unsupported")
        }

        moduleInfoProvider.registerModuleData(module, session.moduleData)

        val enablePluginPhases = FirDiagnosticsDirectives.ENABLE_PLUGIN_PHASES in module.directives
        val isMpp = module.languageVersionSettings.supportsFeature(LanguageFeature.MultiPlatformProjects)
        val firAnalyzerFacade = FirAnalyzerFacade(
            session,
            languageVersionSettings,
            ktFiles,
            lightTreeFiles,
            IrGenerationExtension.getInstances(project),
            lightTreeEnabled,
            enablePluginPhases,
            generateSignatures = module.targetBackend == TargetBackend.JVM_IR_SERIALIZE || isMpp,
            considerDependencyFiles = !isMpp
        )
        val firFiles = firAnalyzerFacade.runResolution()
        val filesMap = firFiles.mapNotNull { firFile ->
            val testFile = module.files.firstOrNull { it.name == firFile.name } ?: return@mapNotNull null
            testFile to firFile
        }.toMap()

        return FirOutputArtifactImpl(session, filesMap, firAnalyzerFacade)
    }
}
