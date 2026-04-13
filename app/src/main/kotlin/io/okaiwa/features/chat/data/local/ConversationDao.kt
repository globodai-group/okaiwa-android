package io.okaiwa.features.chat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY lastMessageAt DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE peerAccountId = :peerAccountId LIMIT 1")
    suspend fun findByPeerAccountId(peerAccountId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE peerDeviceId = :peerDeviceId LIMIT 1")
    suspend fun findByPeerDeviceId(peerDeviceId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conversation: ConversationEntity)

    @Query(
        """
        UPDATE conversations
           SET lastMessageId = :messageId,
               lastMessagePreview = :preview,
               lastMessageAt = :at,
               updatedAt = :at
         WHERE id = :conversationId
        """,
    )
    suspend fun updateLastMessage(
        conversationId: String,
        messageId: String,
        preview: String,
        at: Long,
    )

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :conversationId")
    suspend fun clearUnread(conversationId: String)

    /**
     * Pin or update the peer's Signal identity key. Called from
     * RemoteChatRepository.buildSession on first contact (TOFU
     * commit) and would be called from a future "verify safety
     * number" flow if the user explicitly accepts a key change.
     *
     * NEVER call this to overwrite an existing non-empty
     * peerIdentityKey without the user's explicit confirmation —
     * silent overwrite is exactly the MITM hole TOFU exists to
     * close.
     */
    @Query(
        """
        UPDATE conversations
           SET peerIdentityKey = :peerIdentityKey,
               peerRegistrationId = :peerRegistrationId
         WHERE id = :conversationId
        """,
    )
    suspend fun updatePeerIdentity(
        conversationId: String,
        peerIdentityKey: String,
        peerRegistrationId: Int,
    )

    /**
     * Update the human-readable peer name fields after the receiver
     * resolves them via discovery on first inbound contact.
     */
    @Query(
        """
        UPDATE conversations
           SET peerUsername = :peerUsername
         WHERE id = :conversationId
        """,
    )
    suspend fun updatePeerUsername(conversationId: String, peerUsername: String?)

    @Query(
        """
        UPDATE conversations
           SET unreadCount = unreadCount + 1
         WHERE id = :conversationId
        """,
    )
    suspend fun incrementUnread(conversationId: String)
}
