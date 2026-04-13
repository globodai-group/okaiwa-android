package io.okaiwa.features.chat.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit definition for the relay service — the store-and-forward
 * inbox that carries Signal-framed ciphertext between two Okaiwa
 * devices.
 *
 * Auth: EVERY endpoint requires `Authorization: Bearer {deviceToken}`.
 * The deviceToken is `{deviceId}.{timestamp}.{hmac}` minted by /v1/auth
 * /verify and persisted in the session store — distinct from the
 * accessToken the identity service consumes.
 *
 * Base URL is the same identity host (`https://okaiwa-api.globodai
 * .group/v1/`) — the relay shares the Retrofit instance declared in
 * [io.okaiwa.core.di.AppModule.provideRetrofit].
 */
interface RelayApi {
    /** Enqueue a ciphertext envelope for the recipient's device. */
    @POST("messages/send")
    suspend fun sendMessage(
        @Header("Authorization") bearer: String,
        @Body body: SendMessageRequest,
    ): Response<SendMessageResponse>

    /**
     * Pull every enqueued envelope for a device.
     *
     * The backend is an AdonisJS validator quirk: it wants the deviceId
     * in the request BODY on a GET (unusual but legal per RFC 7231 §4.3.1).
     * OkHttp disallows bodies on GET by default; we use @HTTP with
     * `hasBody = true` so Retrofit routes the serialised body bytes.
     */
    @HTTP(method = "GET", path = "messages/pending", hasBody = true)
    suspend fun getPending(
        @Header("Authorization") bearer: String,
        @Body body: PendingMessagesRequest,
    ): Response<PendingMessagesResponse>

    /** Ack + purge an envelope from the relay inbox after local persist. */
    @DELETE("messages/{messageId}")
    suspend fun deleteMessage(
        @Header("Authorization") bearer: String,
        @Path("messageId") messageId: String,
    ): Response<Unit>
}

@Serializable
data class SendMessageRequest(
    val recipientDeviceId: String,
    /** Base64 of the libsignal CiphertextMessage serialisation. */
    val blob: String,
    /** Client-generated idempotency key (UUID). */
    val messageId: String,
)

@Serializable
data class SendMessageResponse(
    val status: String,
    val messageId: String? = null,
)

@Serializable
data class PendingMessagesRequest(
    val deviceId: String,
)

@Serializable
data class PendingMessagesResponse(
    val messages: List<RelayEnvelope>,
    val count: Int,
)

@Serializable
data class RelayEnvelope(
    val messageId: String,
    /** Base64 of the libsignal CiphertextMessage. */
    val blob: String,
    val enqueuedAt: Long,
    /** Sender's device id — the relay injects it into the envelope so
     *  the receiver can bind the session to the right SignalProtocolAddress.
     *  If absent, fall back to the inner ciphertext's embedded identity
     *  (PreKeyBundle case) or reject the message. */
    val senderDeviceId: String? = null,
    val senderAccountId: String? = null,
)
