package io.okaiwa.core.config

/**
 * Application-wide configuration.
 *
 * Defines server environments, API endpoints, and certificate pinning hashes.
 * In production, only the PRODUCTION environment is used. STAGING exists for
 * internal testing and CI.
 */
object AppConfig {

    /** Current active environment. Set via BuildConfig in release builds. */
    val environment: Environment = Environment.DEV

    /** WebSocket reconnection parameters. */
    const val WS_RECONNECT_BASE_DELAY_MS = 1_000L
    const val WS_RECONNECT_MAX_DELAY_MS = 30_000L
    const val WS_HEARTBEAT_INTERVAL_MS = 30_000L

    /** Database name for Room + SQLCipher. */
    const val DATABASE_NAME = "okaiwa_encrypted.db"

    /** Maximum attachment size in bytes (50 MB). */
    const val MAX_ATTACHMENT_SIZE_BYTES = 50L * 1024 * 1024

    /** Message disappearing timer options in seconds. */
    val DISAPPEARING_TIMER_OPTIONS = listOf(
        0L,          // Off
        30L,         // 30 seconds
        300L,        // 5 minutes
        3_600L,      // 1 hour
        86_400L,     // 1 day
        604_800L,    // 1 week
    )
}

/**
 * Server environment configuration.
 *
 * Each environment defines its own API base URL, WebSocket URL, and
 * certificate pinning SHA-256 hashes.
 */
enum class Environment(
    val apiBaseUrl: String,
    val wsBaseUrl: String,
    val certPinningHashes: List<String>,
) {
    DEV(
        apiBaseUrl = "https://api.dev.okaiwa.io/v1/",
        wsBaseUrl = "wss://ws.dev.okaiwa.io/v1/stream",
        certPinningHashes = listOf(
            "sha256/DEV_PIN_HASH_1_REPLACE_ME",
            "sha256/DEV_PIN_HASH_2_REPLACE_ME",
        ),
    ),
    REC(
        apiBaseUrl = "https://api.rec.okaiwa.io/v1/",
        wsBaseUrl = "wss://ws.rec.okaiwa.io/v1/stream",
        certPinningHashes = listOf(
            "sha256/REC_PIN_HASH_1_REPLACE_ME",
            "sha256/REC_PIN_HASH_2_REPLACE_ME",
        ),
    ),
    PROD(
        apiBaseUrl = "https://api.okaiwa.io/v1/",
        wsBaseUrl = "wss://ws.okaiwa.io/v1/stream",
        certPinningHashes = listOf(
            "sha256/PROD_PIN_HASH_1_REPLACE_ME",
            "sha256/PROD_PIN_HASH_2_REPLACE_ME",
        ),
    ),
}
