package io.okaiwa.features.profile.data.repositories

import io.okaiwa.features.profile.domain.entities.UserProfile
import io.okaiwa.features.profile.domain.repositories.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mock — surfaces a believable profile so the Profil tab UX
 * can be reviewed before the identity service is wired in. Mutations
 * stay local (no network round-trip).
 */
@Singleton
class MockProfileRepository @Inject constructor() : ProfileRepository {

    private val state = MutableStateFlow<UserProfile?>(
        UserProfile(
            userId = "me",
            displayName = "Kevin",
            username = "@asmista",
            phoneNumberE164 = "+971 56 486 1094",
            bio = "Builder · Globodai FZCO",
            avatarUrl = null,
            isVerified = true,
            isOnline = true,
        )
    )

    override fun observeProfile(): Flow<UserProfile?> = state.asStateFlow()

    override suspend fun refresh() {
        // Mock holds the canonical state in-memory — nothing to fetch.
    }

    override suspend fun setDisplayName(newName: String) {
        state.update { it?.copy(displayName = newName) }
    }

    override suspend fun setBio(newBio: String) {
        state.update { it?.copy(bio = newBio.takeIf { s -> s.isNotBlank() }) }
    }

    override suspend fun setUsername(newUsername: String) {
        val normalized = if (newUsername.startsWith("@")) newUsername else "@$newUsername"
        state.update { it?.copy(username = normalized) }
    }

    override suspend fun setAvatar(jpegBytes: ByteArray) {
        // Real impl uploads to /v1/profile/avatar and stores the URL.
        // Mock just flips a marker so the UI can react.
        state.update { it?.copy(avatarUrl = "mock://avatar/${jpegBytes.size}") }
    }

    override suspend fun removeAvatar() {
        state.update { it?.copy(avatarUrl = null) }
    }
}
