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
 * - [versionCode] — monotonic integer = `git rev-list --count HEAD`.
 *   Every commit produces a strictly increasing number — Play Console
 *   and Firebase App Distribution require this for a new upload.
 *
 * - [versionName] = "{MAJOR}.{MINOR}.{versionCode}" — the patch segment
 *   tracks the commit count so every build shows its progress directly
 *   in the tester UI. Bump [okaiwaMajor] / [okaiwaMinor] manually for
 *   user-facing milestones (e.g. beta → stable → 2.0).
 *
 * iOS mirrors this exactly via scripts/set-ios-build-number.sh which
 * writes CFBundleShortVersionString = {MAJOR}.{MINOR}.{count} and
 * CFBundleVersion = {count}.
 *
 * Outside of a git checkout (e.g. a source tarball) the count falls
 * back to 1 so the build still succeeds.
 */
val okaiwaMajor = 1
val okaiwaMinor = 5
val okaiwaVersionCode: Int = runCatching {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        standardOutput = stdout
        isIgnoreExitValue = true
    }
    stdout.toString().trim().toIntOrNull() ?: 1
}.getOrElse { 1 }
val okaiwaVersionName = "$okaiwaMajor.$okaiwaMinor.$okaiwaVersionCode"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

android {
    namespace = "io.okaiwa"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.okaiwa.android"
        minSdk = 26
        targetSdk = 36
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
        // libsignal-android ships bytecode that uses APIs only available
        // on API 26+ (java.time.Instant, java.util.stream.*); core
        // library desugaring backports them through D8 so the same APK
        // runs on the minSdk = 26 surface without crashing on devices
        // that ship older runtimes.
        isCoreLibraryDesugaringEnabled = true
    }

    // Kotlin 2.3 removed the legacy `kotlinOptions` DSL; the
    // configuration now lives on the `kotlin { compilerOptions { … } }`
    // top-level extension instead. Same JVM target, same opt-ins.

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // libsignal-android ships both libsignal_jni.so (production) AND
        // libsignal_jni_testing.so (~70 MB per ABI of test fixtures).
        // The testing variant is only useful for libsignal's own JUnit
        // suite; we never reference it from app code, so excluding it
        // saves ~280 MB across the four ABIs.
        jniLibs {
            excludes += "**/libsignal_jni_testing.so"
        }
    }

    /**
     * APK ABI splits — produce one APK per architecture instead of a
     * universal fat binary. Each split ships only its own .so files
     * (libsignal_jni.so + libTrustWalletCore.so + libsqlcipher.so are
     * the heavy hitters), which keeps the per-device download under
     * Firebase App Distribution's 200 MB limit and matches what the
     * Play Store would generate from an AAB.
     */
    splits {
        abi {
            isEnable = true
            reset()
            // arm64-v8a covers ~99% of modern Android devices in 2026.
            // armeabi-v7a kept for legacy Android Go phones in EM markets.
            // x86 / x86_64 dropped — emulator-only and we can build a
            // dedicated emulator APK on demand.
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
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

    // Encrypted session token storage
    implementation(libs.security.crypto)

    // Firebase (silent push)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // AndroidX Core
    implementation(libs.core.ktx)

    // Credential Manager — omnichannel save for the wallet seed phrase.
    // The runtime dialog offers the user's installed providers
    // (1Password, Dashlane, …) and falls back to Google Password Manager.
    implementation(libs.credentials)
    implementation(libs.credentials.auth)

    // Signal Foundation libsignal — real Curve25519 identity keys, signed
    // pre-keys, PQXDH + Double Ratchet session establishment. Replaces
    // MockSignalKeyBundle. Bundled JNI + .so for arm64-v8a, armeabi-v7a, x86_64.
    implementation(libs.libsignal.android)

    // D8 desugaring runtime — paired with isCoreLibraryDesugaringEnabled.
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Trust Wallet wallet-core — BIP-39 mnemonic generation (256-bit entropy /
    // 24 words) and multi-chain HD derivation (BTC / ETH / SOL) from a single
    // seed. Hosted on GitHub Packages — see settings.gradle.kts.
    implementation(libs.wallet.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test)
}
