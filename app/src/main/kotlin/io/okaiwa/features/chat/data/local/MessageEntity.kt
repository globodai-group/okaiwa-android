package io.okaiwa.features.chat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single message row.
 *
 * Plaintext `body` is stored here intentionally — the encryption layer
 * is Signal Protocol on the wire; once decrypted on-device the UX needs
 * to render the original text on every scroll, which means caching. The
 * row itself is protected at rest by SQLCipher (AES-256 on every page).
 *
 * Delivery state machine:
 *   Pending   → queued locally, not yet POSTed
 *   Sent      → relay returned 202 (not a read receipt — we don't have those yet)
 *   Delivered → future read receipt payload
 *   Failed    → send threw (transient or permanent), UI shows a retry
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "timestamp"]),
        Index(value = ["deliveryState"]),
    ],
)
data class MessageEntity(
    /** Use the relay messageId on inbound, a client-minted UUID on outbound. */
    @PrimaryKey val id: String,
    val conversationId: String,
    /** Device id of the sender — own deviceId on outbound, peer's on inbound. */
    val senderDeviceId: String,
    val body: String,
    val timestamp: Long,
    val isOutbound: Boolean,
    val deliveryState: String,
)

object DeliveryState {
    const val PENDING = "PENDING"
    const val SENT = "SENT"
    const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"
}
