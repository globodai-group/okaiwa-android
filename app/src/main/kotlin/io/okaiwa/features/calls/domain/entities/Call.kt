package io.okaiwa.features.calls.domain.entities

import kotlinx.serialization.Serializable

/**
 * Voice/video call entity.
 *
 * Represents a call session with WebRTC signaling metadata.
 * All calls use SRTP encryption negotiated via SRTP-DTLS.
 * Signaling is routed through the Okaiwa server but media is
 * peer-to-peer when possible (STUN), falling back to TURN relay.
 */
@Serializable
data class Call(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val callerName: String,
    val recipientId: String,
    val recipientName: String,
    val type: CallType,
    val direction: CallDirection,
    val status: CallStatus,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val durationSeconds: Int? = null,
    val isEncrypted: Boolean = true,
) {
    /**
     * Whether the call was missed (incoming, not answered).
     */
    val isMissed: Boolean
        get() = direction == CallDirection.Incoming && status == CallStatus.Missed

    /**
     * Formatted duration string (e.g., "5:23").
     */
    val formattedDuration: String?
        get() = durationSeconds?.let { seconds ->
            val minutes = seconds / 60
            val secs = seconds % 60
            "$minutes:%02d".format(secs)
        }

    /**
     * Display name of the other party (caller if incoming, recipient if outgoing).
     */
    fun otherPartyName(currentUserId: String): String =
        if (callerId == currentUserId) recipientName else callerName
}

@Serializable
enum class CallType {
    Voice,
    Video,
}

@Serializable
enum class CallDirection {
    Incoming,
    Outgoing,
}

@Serializable
enum class CallStatus {
    /** Call is being set up (ICE negotiation). */
    Initiating,
    /** Ringing on the recipient's device. */
    Ringing,
    /** Call is active. */
    InProgress,
    /** Call ended normally. */
    Ended,
    /** Incoming call was not answered. */
    Missed,
    /** Call was declined by the recipient. */
    Declined,
    /** Call failed due to network or technical issues. */
    Failed,
}
