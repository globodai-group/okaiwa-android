import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

/**
 * Build identifiers.
 *
 * - [versionName]  — semantic version controlled manually.
 * - [versionCode]  — monotonic integer derived from the git commit count
 *                    (`git rev-list --count HEAD`). Every commit that lands
 *                    on any branch produces a unique, strictly increasing
 *                    build number — which is exactly what Play Console and
 *                    Firebase App Distribution require for a new upload.
 *
 * The same scheme is used on iOS (CFBundleShortVersionString = versionName,
 * CFBundleVersion = commit count), keeping Android and iOS aligned.
 *
 * Outside of a git checkout (e.g. in a source tarball) the commit count
 * falls back to 1 so the build still succeeds.
 */
val okaiwaVersionName = "1.0.0"
val okaiwaVersionCode: Int = runCatching {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        standardOutput = stdout
        isIgnoreExitValue = true
    }
    stdout.toString().trim().toIntOrNull() ?: 1
}.getOrElse { 1 }

android {
    namespace = "io.okaiwa"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.okaiwa.android"
        minSdk = 26
        targetSdk = 35
        versionCode = okaiwaVersionCode
        versionName = okaiwaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.navigation)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)

    // Kotlin
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)

    // Room + SQLCipher
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)

    // DataStore
    implementation(libs.datastore)

    // Biometric
    implementation(libs.biometric)

    // Firebase (silent push)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // AndroidX Core
    implementation(libs.core.ktx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test)
}
