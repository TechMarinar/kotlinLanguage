/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.session
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.fir.DependencyListForCliModule
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.checkers.registerJsCheckers
import org.jetbrains.kotlin.fir.checkers.registerJvmCheckers
import org.jetbrains.kotlin.fir.checkers.registerNativeCheckers
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.deserialization.SingleModuleDataProvider
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.java.deserialization.JvmClassFileBasedSymbolProvider
import org.jetbrains.kotlin.fir.java.deserialization.OptionalAnnotationClassesProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirBuiltinSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirCloneableSymbolProvider
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectEnvironment
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.name.Name

// TODO: use common classes instead java ones where it makes sense and possible
object FirCommonSessionFactory : FirAbstractSessionFactory() {
    fun createLibrarySession(
        mainModuleName: Name,
        sessionProvider: FirProjectSessionProvider,
        dependencyList: DependencyListForCliModule,
        projectEnvironment: AbstractProjectEnvironment,
        scope: AbstractProjectFileSearchScope,
        packagePartProvider: PackagePartProvider,
        languageVersionSettings: LanguageVersionSettings,
        reuseDependentLibraryProviders: Boolean,
        ignoreExtraJvmClassFileBasedProvider: Boolean
    ): FirSession {
        return createLibrarySession(
            mainModuleName,
            sessionProvider,
            dependencyList.moduleDataProvider,
            languageVersionSettings,
            registerExtraComponents = { it.registerCommonJavaComponents(projectEnvironment.getJavaModuleResolver()) },
            createKotlinScopeProvider = { FirKotlinScopeProvider { _, declaredMemberScope, _, _ -> declaredMemberScope } },
            createProviders = { session, builtinsModuleData, kotlinScopeProvider ->
                val jvmClassFileBasedSymbolProvider = if (ignoreExtraJvmClassFileBasedProvider)
                    null
                else JvmClassFileBasedSymbolProvider(
                    session,
                    dependencyList.moduleDataProvider,
                    kotlinScopeProvider,
                    packagePartProvider,
                    projectEnvironment.getKotlinClassFinder(scope),
                    projectEnvironment.getFirJavaFacade(session, dependencyList.moduleDataProvider.allModuleData.last(), scope)
                )
                if (reuseDependentLibraryProviders) {
                    listOfNotNull(jvmClassFileBasedSymbolProvider) + getDependentLibraryProviders(dependencyList)
                } else {
                    listOfNotNull(
                        jvmClassFileBasedSymbolProvider,
                        FirBuiltinSymbolProvider(session, builtinsModuleData, kotlinScopeProvider),
                        FirCloneableSymbolProvider(session, builtinsModuleData, kotlinScopeProvider),
                        OptionalAnnotationClassesProvider(
                            session,
                            dependencyList.moduleDataProvider,
                            kotlinScopeProvider,
                            packagePartProvider
                        )
                    )
                }
            }
        )
    }

    fun createModuleBasedSession(
        moduleData: FirModuleData,
        sessionProvider: FirProjectSessionProvider,
        projectEnvironment: AbstractProjectEnvironment,
        incrementalCompilationContext: IncrementalCompilationContext?,
        extensionRegistrars: List<FirExtensionRegistrar>,
        languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
        lookupTracker: LookupTracker? = null,
        enumWhenTracker: EnumWhenTracker? = null,
        init: FirSessionConfigurator.() -> Unit = {}
    ): FirSession {
        return createModuleBasedSession(
            moduleData,
            sessionProvider,
            extensionRegistrars,
            languageVersionSettings,
            lookupTracker,
            enumWhenTracker,
            init,
            registerExtraComponents = {
                it.registerCommonJavaComponents(projectEnvironment.getJavaModuleResolver())
                it.registerJavaSpecificResolveComponents()
            },
            registerExtraCheckers = {
                it.registerJvmCheckers()
                it.registerJsCheckers()
                it.registerNativeCheckers()
            },
            createKotlinScopeProvider = { FirKotlinScopeProvider { _, declaredMemberScope, _, _ -> declaredMemberScope } },
            createProviders = { session, kotlinScopeProvider, symbolProvider, generatedSymbolsProvider, dependenciesSymbolProvider ->
                var symbolProviderForBinariesFromIncrementalCompilation: JvmClassFileBasedSymbolProvider? = null
                var optionalAnnotationClassesProviderForBinariesFromIncrementalCompilation: OptionalAnnotationClassesProvider? = null
                incrementalCompilationContext?.let {
                    if (it.precompiledBinariesPackagePartProvider != null && it.precompiledBinariesFileScope != null) {
                        val moduleDataProvider = SingleModuleDataProvider(moduleData)
                        symbolProviderForBinariesFromIncrementalCompilation =
                            JvmClassFileBasedSymbolProvider(
                                session,
                                moduleDataProvider,
                                kotlinScopeProvider,
                                it.precompiledBinariesPackagePartProvider,
                                projectEnvironment.getKotlinClassFinder(it.precompiledBinariesFileScope),
                                projectEnvironment.getFirJavaFacade(session, moduleData, it.precompiledBinariesFileScope),
                                defaultDeserializationOrigin = FirDeclarationOrigin.Precompiled
                            )
                        optionalAnnotationClassesProviderForBinariesFromIncrementalCompilation =
                            OptionalAnnotationClassesProvider(
                                session,
                                moduleDataProvider,
                                kotlinScopeProvider,
                                it.precompiledBinariesPackagePartProvider,
                                defaultDeserializationOrigin = FirDeclarationOrigin.Precompiled
                            )
                    }
                }

                listOfNotNull(
                    symbolProvider,
                    *(incrementalCompilationContext?.previousFirSessionsSymbolProviders?.toTypedArray() ?: emptyArray()),
                    symbolProviderForBinariesFromIncrementalCompilation,
                    generatedSymbolsProvider,
                    dependenciesSymbolProvider,
                    optionalAnnotationClassesProviderForBinariesFromIncrementalCompilation,
                )
            }
        )
    }
}