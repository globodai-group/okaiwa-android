package io.okaiwa.features.profile.domain.repositories

import io.okaiwa.features.profile.domain.entities.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Profile of the currently authenticated user.
 *
 * The backend surface this maps onto is documented in [UserProfile].
 * Callers pull the current profile with [observeProfile] and mutate it
 * with the dedicated setters so the identity service can enforce
 * per-field rules (username uniqueness, display-name rename cooldown,
 * avatar size limits, etc.) server-side.
 */
interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>

    /**
     * Force a re-fetch of the current profile from the identity service
     * and update the cached state. Called immediately after a successful
     * /v1/profile PUT (so the Profile tab reflects the new values
     * without waiting for a tab switch) and on first cold launch after
     * the user verifies (so a fresh install pulls anything that was
     * already on the server from a previous device).
     *
     * No-op on the mock implementation; the remote impl makes the
     * actual GET /v1/profile/me request.
     */
    suspend fun refresh()

    suspend fun setDisplayName(newName: String)
    suspend fun setBio(newBio: String)
    suspend fun setUsername(newUsername: String)
    suspend fun setAvatar(jpegBytes: ByteArray)
    suspend fun removeAvatar()
}
