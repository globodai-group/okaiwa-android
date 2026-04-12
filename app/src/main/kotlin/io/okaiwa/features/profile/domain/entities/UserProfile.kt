package io.okaiwa.features.profile.domain.entities

/**
 * Public profile of the current user — surfaced in the Profil tab.
 *
 * Mirrors Telegram's profile screen fields so the UX is immediately
 * familiar: display name, handle, phone number, optional bio, plus the
 * verified badge for Pro accounts.
 *
 * Backend endpoints to build against this shape:
 *   - GET  /v1/profile/me             → serialize into this entity
 *   - PUT  /v1/profile/display_name   → rename (respects cooldown)
 *   - PUT  /v1/profile/bio            → free-text bio, 140 chars max
 *   - PUT  /v1/profile/username       → change @handle (global unique)
 *   - POST /v1/profile/avatar         → multipart upload, E2EE at rest
 *   - DELETE /v1/profile/avatar       → revert to initial medallion
 *   - POST /v1/profile/publications   → create a story-style "publication"
 *   - GET  /v1/profile/publications   → list ordered by createdAt
 *   - POST /v1/profile/publications/:id/archive → move to archives
 */
data class UserProfile(
    val userId: String,
    val displayName: String,
    val username: String,
    val phoneNumberE164: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val isOnline: Boolean = true,
    val lastSeenAt: Long? = null,
)
