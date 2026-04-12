package io.okaiwa.features.auth.data.repositories

import io.okaiwa.features.auth.domain.entities.User
import io.okaiwa.features.auth.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of [AuthRepository].
 *
 * First-run test build: the UI flow is wired up and screens render,
 * but no network calls are made. Suspend methods return deterministic
 * placeholder values so the ViewModel state machines can progress
 * through their states during UX review.
 *
 * The real implementation lands alongside the identity service wiring.
 */
@Singleton
class StubAuthRepository @Inject constructor() : AuthRepository {

    override fun observeCurrentUser(): Flow<User?> = flowOf(null)

    override suspend fun requestOtp(phoneNumber: String): String {
        return "stub-session-token"
    }

    override suspend fun verifyOtp(sessionToken: String, otpCode: String): User {
        throw NotImplementedError("Auth backend not yet wired — UX test build only")
    }

    override suspend fun registerDevice(phoneNumber: String): User {
        throw NotImplementedError("Auth backend not yet wired — UX test build only")
    }

    override suspend fun refreshToken(): String {
        return "stub-refreshed-token"
    }

    override suspend fun uploadPreKeys(preKeys: ByteArray) {
        // no-op in stub
    }

    override suspend fun signOut() {
        // no-op in stub
    }

    override suspend fun deleteAccount() {
        // no-op in stub
    }
}
