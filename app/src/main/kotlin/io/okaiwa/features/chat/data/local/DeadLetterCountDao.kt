package io.okaiwa.features.chat.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * Minimal DAO for the persisted dead-letter counter. Only two
 * operations by design: atomic bump + full reset per messageId. No
 * bulk reads — the counter is consulted at most once per envelope
 * poll cycle.
 *
 * The UPSERT is written as a single raw `INSERT ... ON CONFLICT DO
 * UPDATE` so there is no TOCTOU window between the read and the
 * write. Same atomicity guarantee as the iOS mirror in
 * `DeadLetterDao.swift`.
 */
@Dao
interface DeadLetterCountDao {

    @Query(
        """
        INSERT INTO dead_letter_counts (messageId, count, updatedAt)
        VALUES (:messageId, 1, :now)
        ON CONFLICT(messageId) DO UPDATE SET
            count = count + 1,
            updatedAt = excluded.updatedAt
        """,
    )
    suspend fun bumpRaw(messageId: String, now: Long)

    @Query("SELECT count FROM dead_letter_counts WHERE messageId = :messageId")
    suspend fun read(messageId: String): Int?

    @Query("DELETE FROM dead_letter_counts WHERE messageId = :messageId")
    suspend fun reset(messageId: String)

    /**
     * Atomic bump + read. Room runs the block in a single SQLite
     * transaction so a concurrent bump from another poll cycle sees
     * our insert and returns the post-increment value correctly.
     */
    @androidx.room.Transaction
    suspend fun bumpAndRead(messageId: String, now: Long): Int {
        bumpRaw(messageId, now)
        return read(messageId) ?: 0
    }
}
