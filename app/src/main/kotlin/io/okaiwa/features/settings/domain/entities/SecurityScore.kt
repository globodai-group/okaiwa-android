package io.okaiwa.features.settings.domain.entities

/**
 * Security score entity.
 *
 * Evaluates the user's security posture based on 7 criteria.
 * Each criterion is worth a fixed number of points, and the total
 * percentage reflects overall security health. Displayed in the
 * Settings screen to encourage security best practices.
 */
data class SecurityScore(
    val hasDevicePasscodeEnabled: Boolean = false,
    val hasBiometricEnabled: Boolean = false,
    val hasVerifiedContacts: Boolean = false,
    val hasDisappearingMessages: Boolean = false,
    val hasPreKeysUpToDate: Boolean = false,
    val hasScreenSecurityEnabled: Boolean = false,
    val hasUpdatedApp: Boolean = false,
) {

    /**
     * Individual criteria with descriptions and weights.
     * Aligned with iOS SecurityScore for consistency.
     */
    val criteria: List<SecurityCriterion>
        get() = listOf(
            SecurityCriterion(
                id = "device_passcode",
                title = "Device passcode",
                description = "Protect your device with a passcode or biometric lock",
                isMet = hasDevicePasscodeEnabled,
                weight = 20,
            ),
            SecurityCriterion(
                id = "biometric",
                title = "Biometric authentication",
                description = "Unlock Okaiwa with fingerprint or face recognition",
                isMet = hasBiometricEnabled,
                weight = 15,
            ),
            SecurityCriterion(
                id = "verified_contacts",
                title = "Verified contacts",
                description = "At least one contact with verified safety number",
                isMet = hasVerifiedContacts,
                weight = 15,
            ),
            SecurityCriterion(
                id = "disappearing",
                title = "Disappearing messages",
                description = "Default disappearing timer enabled",
                isMet = hasDisappearingMessages,
                weight = 10,
            ),
            SecurityCriterion(
                id = "pre_keys",
                title = "Pre-key freshness",
                description = "Keep your encryption keys rotated and up to date",
                isMet = hasPreKeysUpToDate,
                weight = 15,
            ),
            SecurityCriterion(
                id = "screen_security",
                title = "Screen security",
                description = "Prevent screenshots and screen recording",
                isMet = hasScreenSecurityEnabled,
                weight = 10,
            ),
            SecurityCriterion(
                id = "updated",
                title = "App up to date",
                description = "Running the latest version of Okaiwa",
                isMet = hasUpdatedApp,
                weight = 10,
            ),
        )

    /**
     * Total security score as a percentage (0-100).
     */
    val percentage: Int
        get() {
            val totalWeight = criteria.sumOf { it.weight }
            val earnedWeight = criteria.filter { it.isMet }.sumOf { it.weight }
            return if (totalWeight > 0) (earnedWeight * 100) / totalWeight else 0
        }

    /**
     * Number of criteria met out of total.
     */
    val metCount: Int
        get() = criteria.count { it.isMet }

    /**
     * Total number of criteria.
     */
    val totalCount: Int
        get() = criteria.size

    /**
     * Security level classification.
     */
    val level: SecurityLevel
        get() = when {
            percentage >= 90 -> SecurityLevel.Excellent
            percentage >= 70 -> SecurityLevel.Good
            percentage >= 50 -> SecurityLevel.Fair
            else -> SecurityLevel.NeedsImprovement
        }
}

/**
 * Individual security criterion.
 */
data class SecurityCriterion(
    val id: String,
    val title: String,
    val description: String,
    val isMet: Boolean,
    val weight: Int,
)

/**
 * Security level classification.
 */
enum class SecurityLevel(val label: String) {
    Excellent("Excellent"),
    Good("Good"),
    Fair("Fair"),
    NeedsImprovement("Needs improvement"),
}
