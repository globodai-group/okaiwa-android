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
    suspend fun setDisplayName(newName: String)
    suspend fun setBio(newBio: String)
    suspend fun setUsername(newUsername: String)
    suspend fun setAvatar(jpegBytes: ByteArray)
    suspend fun removeAvatar()
}
