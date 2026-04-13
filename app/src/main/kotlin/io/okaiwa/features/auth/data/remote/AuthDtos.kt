package io.okaiwa.features.auth.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for the identity service auth endpoints.
 *
 * These mirror the Vine validators declared in
 * `okaiwa-server/identity/app/controllers/auth_controller.ts`. Field
 * names match the JSON payload exactly — deviations would force us to
 * maintain @SerialName aliases on every property, which is noise.
 *
 * Phone numbers NEVER appear on this wire. The client hashes them with
 * SHA-256 before anything is sent over HTTPS.
 */

/** Body of `POST /v1/auth/register`. */
@Serializable
data class RegisterRequest(
    /** Lowercase hex SHA-256 of the E.164 phone number, 64 chars. */
    val phoneHash: String,

    /** Curve25519 identity public key, base64-encoded. */
    val identityPublicKey: String,

    /** Signal Protocol signed pre-key bundle. */
    val signedPreKey: SignedPreKeyDto,

    /** Signal Protocol registration ID — 14-bit positive int. */
    val registrationId: Int,

    /** Optional opt-in username — 3..32 chars, `[a-zA-Z0-9_]`. */
    val username: String? = null,
)

@Serializable
data class SignedPreKeyDto(
    val keyId: Int,
    val publicKey: String,
    val signature: String,
)

/** Response to `POST /v1/auth/register`. */
@Serializable
data class RegisterResponse(
    val accountId: String,
    val status: String,
)

/** Body of `POST /v1/auth/verify`. */
@Serializable
data class VerifyRequest(
    val phoneHash: String,
    /** 6-digit numeric code delivered by SMS + email (Brevo). */
    val code: String,
)

/** Response to `POST /v1/auth/verify` and `POST /v1/auth/refresh`. */
@Serializable
data class SessionTokenResponse(
    val sessionToken: String,
    val refreshToken: String,
    val expiresIn: Int,
)

/** Body of `POST /v1/auth/refresh`. */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

/**
 * Shape of error responses from the identity service. Endpoints return
 * a generic `error` string to avoid enumeration (e.g. the "does this
 * phone exist" oracle). Kept non-nullable because a missing body is
 * itself an error condition we handle upstream.
 */
@Serializable
data class ApiErrorBody(
    val error: String,
)
