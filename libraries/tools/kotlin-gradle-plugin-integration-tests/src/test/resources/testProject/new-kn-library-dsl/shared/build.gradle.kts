import org.jetbrains.kotlin.gradle.plugin.cocoapods.withPodspec

plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods") // TODO separate project for tests
}

kotlin {
    linuxX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

kotlinArtifacts {
    Native.Library("mylib") {
        target = linuxX64
        kotlinOptions {
            freeCompilerArgs += "-Xmen=pool"
        }
    }
    Native.Library("myslib") {
        target = linuxX64
        isStatic = false
        modes(DEBUG)
        addModule(project(":lib"))
        kotlinOptions {
            verbose = false
            freeCompilerArgs = emptyList()
        }
    }
    Native.Framework("myframe") {
        modes(DEBUG, RELEASE)
        target = iosArm64
        isStatic = false
        embedBitcode = EmbedBitcodeMode.MARKER
        kotlinOptions {
            verbose = false
        }
    }
    Native.FatFramework("myfatframe") {
        targets(iosX64, iosSimulatorArm64)
        embedBitcode = EmbedBitcodeMode.DISABLE
        kotlinOptions {
            suppressWarnings = false
        }
    }
    Native.XCFramework {
        targets(iosX64, iosArm64, iosSimulatorArm64)
        setModules(
            project(":shared"),
            project(":lib")
        )

        withPodspec {
            attribute("version", "666.66")
            attribute("license", "LGPL'ish")
            attribute("some_map", "{ 'well' => 'you know', 'some_map_key' => false, 'another one?' => { 'yeeah' => 'sure!' } }")
            attribute("some_list", "[ 'coolbean1', 'coolbean2', 'coolbean3' ]")
            attribute("ios.deployment_target", "10.0")

            rawStatement("""
            |    spec.dependency 'SSZipArchive'
            """.trimMargin())
        }
    }
}
