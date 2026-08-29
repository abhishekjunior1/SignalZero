plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // JVM target exists so the evaluation harness can run the SHIPPED Kotlin
    // headlessly. Scoring a Python re-implementation would measure a copy that
    // silently drifts from the code that actually ships.
    jvm()

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
            }
        }
    }

    // Three iOS targets: device (arm64), Intel simulator, Apple-silicon
    // simulator. On an M-series Mac the last one is what actually runs.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            // Static linking keeps the Xcode side to a single artifact with
            // no dynamic-library packaging step.
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

android {
    namespace = "com.medic.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Runs the triage eval against the shipped Kotlin.
 *   ./gradlew :shared:evalCli --args="eval/cases/triage.json safetytree"
 */
tasks.register<JavaExec>("evalCli") {
    group = "verification"
    description = "Run the triage evaluation CLI over a case file"
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.medic.app.eval.EvalCliKt")
    // JavaExec defaults to the module dir; case paths are repo-relative.
    workingDir = rootDir
}
