package io.okaiwa.features.profile.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT

/**
 * Retrofit definition for the identity service profile endpoints.
 *
 * Auth: every authenticated route requires `Authorization: Bearer
 * <accessToken>`. The token is the HMAC-signed value minted by
 * `POST /v1/auth/verify` (`{accountId}.{timestamp}.{hmac}`); the
 * server-side SessionAuthMiddleware verifies the signature and
 * derives accountId server-side, so the client never needs to send
 * accountId in a header (which would be a forgeable bypass — see
 * security review on android@386d11d).
 */
interface ProfileApi {
    @PUT("profile")
    suspend fun updateProfile(
        @Header("Authorization") bearer: String,
        @Body body: UpdateProfileRequest,
    ): Response<UpdateProfileResponse>

    /**
     * Fetch the authenticated caller's own profile. Returns even
     * private fields and the full exposedWalletAddresses regardless
     * of visibility — the caller IS the owner.
     */
    @GET("profile/me")
    suspend fun getMyProfile(
        @Header("Authorization") bearer: String,
    ): Response<MyProfileResponse>
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

/** Body of GET /v1/profile/me. */
@Serializable
data class MyProfileResponse(
    val accountId: String,
    val username: String? = null,
    val identityPublicKey: String? = null,
    val profile: UpdatedProfile? = null,
)
