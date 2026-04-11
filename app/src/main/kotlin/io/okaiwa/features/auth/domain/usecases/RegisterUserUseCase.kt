package io.okaiwa.features.auth.domain.usecases

import io.okaiwa.core.errors.AppError
import io.okaiwa.features.auth.domain.entities.User
import io.okaiwa.features.auth.domain.repositories.AuthRepository
import javax.inject.Inject

/**
 * Use case for user registration.
 *
 * Orchestrates the full registration flow:
 * 1. Validate the phone number (E.164 format).
 * 2. Generate Signal Protocol identity keys via SignalCore JNI.
 * 3. Hash the phone number (SHA-256) — server never sees plaintext.
 * 4. Request OTP from the server.
 * 5. Return session token for OTP verification.
 *
 * The actual OTP verification is handled by [VerifyOtpUseCase].
 */
class RegisterUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {

    /**
     * Initiate registration for the given phone number.
     *
     * @param phoneNumber Raw phone number input from user.
     * @return Result containing the session token on success.
     */
    suspend operator fun invoke(phoneNumber: String): Result<String> = runCatching {
        val sanitized = sanitizePhoneNumber(phoneNumber)

        if (!isValidE164(sanitized)) {
            throw AppError.Auth.RegistrationFailed(
                "Invalid phone number format. Expected E.164 (e.g., +33612345678)."
            )
        }

        // Request OTP — the repository handles phone hashing and key generation
        authRepository.requestOtp(sanitized)
    }

    private fun sanitizePhoneNumber(phone: String): String =
        phone.filter { it.isDigit() || it == '+' }

    private fun isValidE164(phone: String): Boolean =
        phone.matches(Regex("^\\+[1-9]\\d{6,14}$"))
}

/**
 * Use case for OTP verification and registration completion.
 */
class VerifyOtpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {

    /**
     * Verify OTP and complete registration.
     *
     * @param sessionToken Token from [RegisterUserUseCase].
     * @param otpCode 6-digit OTP code from SMS.
     * @return Result containing the authenticated [User].
     */
    suspend operator fun invoke(sessionToken: String, otpCode: String): Result<User> = runCatching {
        require(otpCode.length == 6 && otpCode.all { it.isDigit() }) {
            "OTP must be exactly 6 digits."
        }

        // Verify OTP and complete registration.
        // Pre-key generation and upload is handled by the repository
        // implementation during the verifyOtp flow.
        authRepository.verifyOtp(sessionToken, otpCode)
    }
}
