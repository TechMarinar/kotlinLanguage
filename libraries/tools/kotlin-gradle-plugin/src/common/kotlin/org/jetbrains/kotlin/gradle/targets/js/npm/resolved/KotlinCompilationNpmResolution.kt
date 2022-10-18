/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolved

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import java.io.Serializable

/**
 * Resolved [NpmProject]
 */
class KotlinCompilationNpmResolution(
    private val objectFactory: ObjectFactory,
    val npmProject: NpmProject,
    val internalCompositeDependencies: Collection<GradleNodeModule>,
    val externalGradleDependencies: Collection<GradleNodeModule>,
    private val _externalNpmDependencies: Collection<NpmDependencyDeclaration>,
    val packageJson: PackageJson
) : Serializable {
    val externalNpmDependencies
        get() = _externalNpmDependencies
            .map {
                NpmDependency(
                    objectFactory = objectFactory,
                    scope = it.scope,
                    name = it.name,
                    version = it.version,
                )
            }
}