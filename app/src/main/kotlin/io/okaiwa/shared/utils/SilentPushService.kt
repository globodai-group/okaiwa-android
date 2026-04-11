package io.okaiwa.shared.utils

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.okaiwa.BuildConfig

/**
 * Firebase Cloud Messaging service for silent push notifications.
 *
 * Receives data-only push messages from the server to trigger
 * background operations. This service NEVER displays notifications
 * directly — it only signals the app to fetch new data via WebSocket
 * or API calls.
 *
 * Data-only messages:
 * - `type: "new_message"` — Trigger WebSocket reconnect to fetch messages.
 * - `type: "key_update"` — Re-fetch pre-keys for a contact.
 * - `type: "call_incoming"` — Trigger incoming call UI.
 * - `type: "wallet_tx"` — Refresh wallet balance.
 *
 * No user-visible content is included in the push payload.
 * Message content is always fetched through the encrypted channel.
 */
@AndroidEntryPoint
class SilentPushService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SilentPushService"

        // Push event types
        const val TYPE_NEW_MESSAGE = "new_message"
        const val TYPE_KEY_UPDATE = "key_update"
        const val TYPE_CALL_INCOMING = "call_incoming"
        const val TYPE_WALLET_TX = "wallet_tx"
    }

    /**
     * Called when a data-only message is received from FCM.
     *
     * Routes the event to the appropriate handler based on the `type` field.
     * No notification is displayed — this is purely a background trigger.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Reject any messages with a notification payload (data-only enforcement)
        if (remoteMessage.notification != null) {
            Log.w(TAG, "Received notification payload — ignoring. Data-only messages expected.")
            return
        }

        val data = remoteMessage.data
        val type = data["type"]

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Received silent push: type=$type")
        }

        when (type) {
            TYPE_NEW_MESSAGE -> handleNewMessage(data)
            TYPE_KEY_UPDATE -> handleKeyUpdate(data)
            TYPE_CALL_INCOMING -> handleIncomingCall(data)
            TYPE_WALLET_TX -> handleWalletTransaction(data)
            else -> Log.w(TAG, "Unknown push type: $type")
        }
    }

    /**
     * Called when the FCM registration token is refreshed.
     *
     * Uploads the new token to the Okaiwa server so it can route
     * push notifications to this device.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "FCM token refreshed")
        }
        // TODO: Upload token to Okaiwa server via AuthRepository
        // The token must be encrypted before transmission.
    }

    /**
     * Handle new message signal.
     * Triggers WebSocket reconnect or API fetch to retrieve encrypted messages.
     */
    private fun handleNewMessage(data: Map<String, String>) {
        val conversationId = data["conversation_id"]
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "New message signal for conversation: ${conversationId?.take(8)}")
        }
        // TODO: Trigger WebSocket reconnect via MessageSyncWorker
        // WorkManager.getInstance(this).enqueue(MessageSyncWorkRequest)
    }

    /**
     * Handle key update signal.
     * Re-fetches pre-keys when a contact changes their identity key.
     */
    private fun handleKeyUpdate(data: Map<String, String>) {
        val userId = data["user_id"]
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Key update signal for user: ${userId?.take(8)}")
        }
        // TODO: Trigger pre-key refresh via KeySyncWorker
    }

    /**
     * Handle incoming call signal.
     * Displays the incoming call UI with WebRTC signaling.
     */
    private fun handleIncomingCall(data: Map<String, String>) {
        val callId = data["call_id"]
        val callerId = data["caller_id"]
        val callType = data["call_type"] // "voice" or "video"
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Incoming call signal: callId=${callId?.take(8)}, type=$callType")
        }
        // TODO: Start IncomingCallService with foreground notification
    }

    /**
     * Handle wallet transaction signal.
     * Refreshes wallet balance after a confirmed transaction.
     */
    private fun handleWalletTransaction(data: Map<String, String>) {
        val txHash = data["tx_hash"]
        val chain = data["chain"]
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Wallet transaction signal: chain=$chain, tx=${txHash?.take(10)}")
        }
        // TODO: Trigger balance refresh via WalletSyncWorker
    }
}
