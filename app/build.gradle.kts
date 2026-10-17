import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.parcha.spoilnot"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.parcha.spoilnot"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "com.parcha.spoilnot.HiltTestRunner"
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Spoil Not Dev")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "Spoil Not")
        }
    }

    // Release signing comes from keystore.properties (never committed, DESIGN 13.3). Until the upload keystore
    // exists (M6), release builds use the debug key so they can be installed for testing against real Firebase.
    val keystoreFile = rootProject.file("keystore.properties")
    signingConfigs {
        if (keystoreFile.exists()) {
            val props = Properties().apply { keystoreFile.inputStream().use(::load) }
            create("upload") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric renders Compose on the JVM for component and screenshot tests (DESIGN 13.1).
        unitTests.isIncludeAndroidResources = true
    }
}

// Only dev debug builds talk to the Firebase Emulator Suite (DESIGN 7.4). The Android emulator reaches the
// host at 10.0.2.2; pass -Pspoilnot.emulatorHost=<LAN IP> to use a physical phone.
val emulatorHost = providers.gradleProperty("spoilnot.emulatorHost").orElse("10.0.2.2")
androidComponents {
    onVariants { variant ->
        val useEmulator = variant.flavorName == "dev" && variant.buildType == "debug"
        val host = if (useEmulator) emulatorHost.get() else ""
        variant.buildConfigFields?.put(
            "FIREBASE_EMULATOR_HOST",
            BuildConfigField("String", "\"$host\"", "Firebase emulator host; empty means real Firebase"),
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.timber)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.browser)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

// Robolectric reaches into JDK internals that Java 17+ hides by default.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
}

// Screenshots are written here by ./gradlew recordRoborazziDevDebug for review; a plain test run only renders.
roborazzi {
    outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
}
