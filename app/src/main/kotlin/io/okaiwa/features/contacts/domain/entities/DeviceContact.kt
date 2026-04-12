package io.okaiwa.features.contacts.domain.entities

/**
 * A contact surfaced by the device's system address book.
 *
 * [isOnOkaiwa] is determined post-sync by hashing the phone number with
 * PBKDF2 and asking the identity service which hashes it already knows.
 * Until the identity service is wired up the flag is mocked to a stable
 * subset of contacts so the UI split (message vs invite) can be
 * reviewed end-to-end.
 */
data class DeviceContact(
    val id: String,
    val displayName: String,
    val phoneNumberE164: String,
    val avatarUri: String? = null,
    val isOnOkaiwa: Boolean = false,
    val okaiwaUsername: String? = null,
) {
    /** First-letter initial used in the placeholder avatar medallion. */
    val initial: String
        get() = displayName.trim().firstOrNull()?.uppercase() ?: "?"
}
