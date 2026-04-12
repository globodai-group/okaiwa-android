package io.okaiwa.core.config

import io.okaiwa.BuildConfig

/**
 * Development-only conveniences.
 *
 * Every flag here is gated on [BuildConfig.DEBUG] so it is **never**
 * compiled into a release APK. Release builds strip these checks at
 * compile time (R8 dead-code elimination).
 *
 * Add a flag here only if it meaningfully accelerates tester iteration
 * AND it cannot cause harm if accidentally exposed. The magic OTP below
 * is a good example: it only short-circuits the SMS round-trip, it does
 * not bypass server-side verification — in production the server rejects
 * any OTP that doesn't match a real SMS challenge, regardless of what
 * the client sends.
 */
object DevConfig {

    /**
     * Magic OTP code that auto-passes the verification screen during
     * development, so testers don't have to wait for (or pay for) a real
     * SMS while iterating on the onboarding UX.
     *
     * Debug builds only. In release builds [isDevOtpAccepted] always
     * returns false because [BuildConfig.DEBUG] is false.
     */
    private const val DEV_MAGIC_OTP = "000000"

    /**
     * Returns true if the given OTP should be accepted locally without
     * a server round-trip. Only ever true in debug builds.
     */
    fun isDevOtpAccepted(otp: String): Boolean =
        BuildConfig.DEBUG && otp == DEV_MAGIC_OTP
}
