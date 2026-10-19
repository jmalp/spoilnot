// Macrobenchmarks and the baseline profile generator (DESIGN 13.1). They drive the dev benchmark build (release
// code against the local emulators) on an Android emulator or phone; nothing here ships. See docs/RUNBOOK.md.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.parcha.spoilnot.benchmark"
    compileSdk = 37

    defaultConfig {
        // BaselineProfileRule needs API 28 or later.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Benchmarks run on emulators here; their numbers compare builds, they are not phone timings.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
        missingDimensionStrategy("env", "dev")
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
    // Only the benchmark variant exists, so it always targets the app's dev benchmark build.
    beforeVariants { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
}
