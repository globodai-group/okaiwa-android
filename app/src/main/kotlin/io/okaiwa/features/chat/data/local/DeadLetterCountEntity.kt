package io.okaiwa.features.chat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted replacement for the in-memory `decryptFailureCounts`
 * `ConcurrentHashMap` that the polling service used to keep.
 *
 * The in-memory counter reset on every app launch, so a hostile relay
 * could cycle through `MAX_DECRYPT_RETRIES - 1` failed attempts, wait
 * for the user to background the app (OOM kill, manual swipe), and
 * loop the same poison envelope indefinitely — no dead-letter ever
 * fires (P1 from the cross-platform polling security review).
 *
 * Writing to the SQLCipher-encrypted `dead_letter_counts` table means
 * the counter survives cold starts and the ceiling is actually
 * enforced. Rows are deleted on successful decrypt, on ack+drop, and
 * on signOut via `OkaiwaDatabase.clearAllTables()`.
 */
@Entity(tableName = "dead_letter_counts")
data class DeadLetterCountEntity(
    @PrimaryKey val messageId: String,
    val count: Int,
    val updatedAt: Long,
)
