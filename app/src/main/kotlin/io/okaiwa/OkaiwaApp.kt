package io.okaiwa

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * Okaiwa application entry point.
 *
 * Responsibilities:
 *   - Bootstraps Hilt dependency injection.
 *   - Loads the Trust Wallet wallet-core JNI library on startup so any
 *     wallet UI opened during the session can instantiate [wallet.core
 *     .jni.HDWallet] immediately. The library ships four .so variants
 *     (arm64-v8a, armeabi-v7a, x86_64, x86) inside the AAR.
 *
 * Screen security (FLAG_SECURE) is NOT applied globally here — it is
 * an opt-in per sensitive screen via [io.okaiwa.shared.utils.SecureScreen].
 * See ScreenSecurity.kt for rationale.
 */
@HiltAndroidApp
class OkaiwaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        loadWalletCore()
    }

    /**
     * Loads the native library behind wallet-core. A failure here is
     * non-fatal for the rest of the app (chat, auth, settings still
     * work), so we swallow the UnsatisfiedLinkError into a Log.e. The
     * wallet UI guards its entry points against a missing binding —
     * the user sees "Wallet indisponible sur cet appareil" rather than
     * a crash.
     */
    private fun loadWalletCore() {
        try {
            System.loadLibrary(TRUST_WALLET_CORE_LIB)
        } catch (t: UnsatisfiedLinkError) {
            Log.e(TAG, "TrustWalletCore native lib failed to load", t)
        }
    }

    private companion object {
        const val TAG = "OkaiwaApp"
        const val TRUST_WALLET_CORE_LIB = "TrustWalletCore"
    }
}
