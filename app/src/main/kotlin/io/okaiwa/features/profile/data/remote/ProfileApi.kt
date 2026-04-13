package io.okaiwa.features.profile.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PUT

/**
 * Retrofit definition for the identity service profile endpoints.
 *
 * Authentication: the server currently reads `x-account-id` from the
 * request header instead of validating a session token — the auth
 * middleware + sessions table land with the next migration. Until then
 * the mobile client passes the stashed accountId explicitly.
 */
interface ProfileApi {
    @PUT("profile")
    suspend fun updateProfile(
        @Header("x-account-id") accountId: String,
        @Body body: UpdateProfileRequest,
    ): Response<UpdateProfileResponse>
}

/**
 * Body of PUT /v1/profile. All fields are optional — the server
 * accepts partial updates so the client can set, say, just a username
 * at profile-setup time and defer the avatar to later.
 *
 * Field constraints mirror the server's Vine validator:
 *   - username: [a-zA-Z0-9_]{3,32}
 *   - displayName: 1..64 chars
 *   - bio: max 300 chars
 *   - visibility: one of public | contacts | private
 */
@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val visibility: String? = null,
    val exposedWalletAddresses: List<String>? = null,
)

@Serializable
data class UpdateProfileResponse(
    val accountId: String,
    val username: String? = null,
    val profile: UpdatedProfile? = null,
)

@Serializable
data class UpdatedProfile(
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val visibility: String? = null,
    val exposedWalletAddresses: List<String>? = null,
)
