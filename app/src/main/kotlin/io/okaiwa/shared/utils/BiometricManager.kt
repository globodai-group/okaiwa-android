package io.okaiwa.shared.utils

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Biometric authentication manager.
 *
 * Wraps AndroidX Biometric APIs to provide a coroutine-based interface
 * for biometric authentication. Supports fingerprint, face, and iris
 * recognition depending on device capabilities.
 *
 * Used to protect sensitive operations:
 * - App unlock
 * - Wallet transactions
 * - Mnemonic export
 * - Account deletion
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Result of a biometric authentication attempt.
     */
    sealed class BiometricResult {
        data object Success : BiometricResult()
        data class Error(val errorCode: Int, val message: String) : BiometricResult()
        data object Cancelled : BiometricResult()
        data object NotAvailable : BiometricResult()
    }

    /**
     * Check if biometric authentication is available on this device.
     *
     * @return true if the device has enrolled biometrics.
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Check the detailed biometric status.
     *
     * @return A human-readable status description.
     */
    fun getBiometricStatus(): String {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
        )) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS ->
                "Biometric authentication is available"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "No biometric hardware detected"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric hardware is currently unavailable"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No biometrics enrolled. Please set up fingerprint or face recognition in device settings."
            else -> "Biometric authentication is unavailable"
        }
    }

    /**
     * Show a biometric authentication prompt.
     *
     * @param activity The [FragmentActivity] hosting the prompt.
     * @param title Title displayed in the biometric dialog.
     * @param subtitle Subtitle displayed in the biometric dialog.
     * @param negativeButtonText Text for the cancel/fallback button.
     * @return [BiometricResult] indicating success, error, or cancellation.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Verify your identity to continue",
        negativeButtonText: String = "Cancel",
    ): BiometricResult {
        if (!isBiometricAvailable()) {
            return BiometricResult.NotAvailable
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) {
                        continuation.resume(BiometricResult.Success)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        val result = if (
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        ) {
                            BiometricResult.Cancelled
                        } else {
                            BiometricResult.Error(errorCode, errString.toString())
                        }
                        continuation.resume(result)
                    }
                }

                override fun onAuthenticationFailed() {
                    // Don't resume — the prompt stays open for retry.
                    // Only onAuthenticationError or onAuthenticationSucceeded are terminal.
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
                .build()

            prompt.authenticate(promptInfo)

            continuation.invokeOnCancellation {
                prompt.cancelAuthentication()
            }
        }
    }
}
