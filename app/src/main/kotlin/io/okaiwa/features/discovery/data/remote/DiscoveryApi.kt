package io.okaiwa.features.discovery.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit definition for the identity service discovery endpoints.
 *
 * Privacy contract enforced server-side (see okaiwa-server/identity/
 * app/controllers/discovery_controller.ts):
 *   - Phone-based discovery accepts ONLY pre-hashed values.
 *   - Username + wallet search are exact-match (no enumeration).
 *   - Responses NEVER include the phoneHash itself.
 *
 * Every response carries the recipient's `deviceId` so the sender can
 * route an encrypted blob via the relay's POST /v1/messages/send.
 */
interface DiscoveryApi {
    @POST("discovery/phone-hashes")
    suspend fun discoverByPhoneHashes(@Body body: PhoneHashesRequest): Response<PhoneHashesResponse>

    @GET("discovery/username/{username}")
    suspend fun searchByUsername(@Path("username") username: String): Response<DiscoveredUser>

    @GET("discovery/wallet/{address}")
    suspend fun searchByWallet(@Path("address") address: String): Response<DiscoveredUser>
}

@Serializable
data class PhoneHashesRequest(
    /** Lowercase hex SHA-256 hashes — max 1000 per request. */
    val hashes: List<String>,
)

@Serializable
data class PhoneHashesResponse(
    val matches: List<DiscoveredUser>,
    val total: Int,
)

/**
 * Public-facing slice of an Okaiwa account exposed by discovery.
 *
 * No phone hash, no email, no per-session metadata. The identity public
 * key + registrationId let the sender build a Signal session against
 * this user; the deviceId routes the resulting blob to the right
 * relay inbox.
 */
@Serializable
data class DiscoveredUser(
    val accountId: String,
    val username: String? = null,
    val identityPublicKey: String,
    val registrationId: Int,
    val deviceId: String? = null,
    val profile: DiscoveredProfile? = null,
)

@Serializable
data class DiscoveredProfile(
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
)
