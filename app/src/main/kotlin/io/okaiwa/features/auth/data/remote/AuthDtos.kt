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
    /** Relay-addressable device identifier (UUIDv4). */
    val deviceId: String? = null,
    val status: String,
)

/** Body of `POST /v1/auth/verify`. */
@Serializable
data class VerifyRequest(
    val phoneHash: String,
    /** 6-digit numeric code delivered by SMS + email (Brevo). */
    val code: String,
)

/**
 * Response to `POST /v1/auth/verify`. The verify endpoint is the only
 * place the relay deviceToken is minted — refresh re-issues the session
 * tokens but NOT the deviceToken (which is long-lived per the relay's
 * MAX_TOKEN_AGE_SECONDS check).
 */
@Serializable
data class SessionTokenResponse(
    val sessionToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    /** Relay-addressable device identifier — only present on verify. */
    val deviceId: String? = null,
    /**
     * HMAC-signed token of the form `{deviceId}.{timestamp}.{hmac}`.
     * Required as `Authorization: Bearer <deviceToken>` on every relay
     * request (POST /v1/messages/send, GET /v1/messages/pending,
     * DELETE /v1/messages/:id). Only present on verify.
     */
    val deviceToken: String? = null,
)

/** Body of `POST /v1/auth/refresh`. */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

/** Body of `POST /v1/auth/login` — symmetric to register but for existing accounts. */
@Serializable
data class LoginRequest(
    val phoneHash: String,
)

/** Response to `POST /v1/auth/login`. */
@Serializable
data class LoginResponse(
    val accountId: String,
    val status: String,
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
