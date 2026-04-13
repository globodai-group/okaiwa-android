package io.okaiwa.features.keys.data.remote

import io.okaiwa.features.auth.data.remote.SignedPreKeyDto
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit definition for the identity service pre-key endpoints.
 *
 * Two responsibilities:
 *   1. POST /v1/keys/prekeys — upload our one-time pre-key batch + the
 *      signed pre-key + (PQXDH) kyber pre-key after OTP verify. Auth:
 *      `Authorization: Bearer {accessToken}` from [io.okaiwa
 *      .features.auth.data.session.SessionStore]. The server derives
 *      accountId from the token.
 *
 *   2. GET /v1/keys/prekey/:deviceId — anonymous lookup that returns
 *      ONE one-time pre-key (consumed server-side) + the signed pre-key
 *      + the identity key + the registrationId for the target device.
 *      No auth required — the device id itself is the routing handle
 *      and every online user publishes the same bundle to peers.
 *
 * The shape of the 2nd endpoint is fixed by the existing backend; the
 * client must tolerate a null `preKey` (server out of OPKs) and fall
 * back to signed-pre-key-only session setup, which still works at the
 * protocol level — X3DH just skips the one-time leg.
 */
interface KeyApi {
    @POST("keys/prekeys")
    suspend fun uploadPreKeys(
        @Header("Authorization") bearer: String,
        @Body body: UploadPreKeysRequest,
    ): Response<UploadPreKeysResponse>

    @GET("keys/prekey/{deviceId}")
    suspend fun fetchPreKey(
        @Path("deviceId") deviceId: String,
    ): Response<FetchPreKeyResponse>
}

/** Wire-format DTO — single one-time pre-key public key + its id. */
@Serializable
data class PreKeyDto(
    val keyId: Int,
    val publicKey: String,
)

/**
 * Wire-format DTO — Kyber (PQXDH) pre-key public key + the signature
 * from the identity key. Backend support for the KYBER field is tracked
 * separately (the non-kyber bundle currently returned by /v1/keys/
 * prekey/:deviceId forces us to reject the send — see
 * [FetchPreKeyResponse.kyberPreKey]).
 */
@Serializable
data class KyberPreKeyDto(
    val keyId: Int,
    val publicKey: String,
    val signature: String,
)

/** Body of POST /v1/keys/prekeys. */
@Serializable
data class UploadPreKeysRequest(
    /** 1..100 one-time pre-keys. The server stores them and consumes one
     *  per /v1/keys/prekey/:deviceId GET. */
    val preKeys: List<PreKeyDto>,
    /** Optional signed pre-key refresh. Sent unconditionally on first
     *  upload so the server has the same record we persisted locally. */
    val signedPreKey: SignedPreKeyDto? = null,
    /** Kyber (PQXDH) pre-key. Required for libsignal 0.86+ session setup
     *  — if the backend doesn't understand this field yet, extend the
     *  validator on the server side. */
    val kyberPreKey: KyberPreKeyDto? = null,
)

/** Response of POST /v1/keys/prekeys. */
@Serializable
data class UploadPreKeysResponse(
    val status: String,
    /** Server-echoed count of stored OPKs. */
    val storedPreKeyCount: Int = 0,
)

/**
 * Response of GET /v1/keys/prekey/:deviceId.
 *
 * The server returns the bundle the caller needs to build a PreKeyBundle
 * and hand it to libsignal's SessionBuilder.process(bundle). Fields map
 * 1:1 onto the PreKeyBundle constructor parameters.
 */
@Serializable
data class FetchPreKeyResponse(
    /** Base64-encoded Curve25519 public key of the peer's identity. */
    val identityKey: String,
    val registrationId: Int,
    /** Optional device id — matches the :deviceId path param. Some
     *  backend revisions omit this — we accept either shape. */
    val deviceId: String? = null,
    val signedPreKey: SignedPreKeyDto,
    /** Null when the peer has run out of OPKs. The caller falls back
     *  to a signed-pre-key-only session if this happens. */
    val preKey: PreKeyDto? = null,
    /** Kyber (PQXDH) pre-key. When null the sender cannot build a
     *  0.86-compatible PreKeyBundle — report a clear error and surface
     *  a developer-visible log (no sensitive material in the log
     *  message). */
    val kyberPreKey: KyberPreKeyDto? = null,
)
