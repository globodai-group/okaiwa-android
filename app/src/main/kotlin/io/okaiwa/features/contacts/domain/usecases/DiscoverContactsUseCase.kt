package io.okaiwa.features.contacts.domain.usecases

import io.okaiwa.features.contacts.domain.entities.Contact
import javax.inject.Inject

/**
 * Use case for contact discovery.
 *
 * Discovers which of the user's device contacts are registered Okaiwa users.
 * The process preserves privacy:
 * 1. Read contacts from the device address book (requires READ_CONTACTS permission).
 * 2. Hash each phone number with SHA-256.
 * 3. Send the hashes to the server in batches.
 * 4. Server returns which hashes correspond to registered users.
 * 5. Merge results with local contact names and avatars.
 *
 * The server never receives plaintext phone numbers.
 */
class DiscoverContactsUseCase @Inject constructor(
    // In production: ContactsRepository, DeviceContactsDataSource
) {

    /**
     * Discover registered Okaiwa users from the device's contact list.
     *
     * @return Result containing a list of [Contact]s with registration status.
     */
    suspend operator fun invoke(): Result<List<Contact>> = runCatching {
        // 1. Read device contacts (requires permission check at presentation layer)
        val deviceContacts = readDeviceContacts()

        // 2. Hash phone numbers
        val phoneHashes = deviceContacts.map { (name, phone) ->
            name to hashPhoneNumber(phone)
        }

        // 3. Query server for registered hashes
        val registeredHashes = queryRegisteredHashes(phoneHashes.map { it.second })

        // 4. Build contact list
        phoneHashes.map { (name, hash) ->
            Contact(
                id = hash,
                phoneHash = hash,
                displayName = name,
                avatarUrl = null,
                isRegistered = hash in registeredHashes,
            )
        }.sortedWith(
            compareByDescending<Contact> { it.isRegistered }
                .thenBy { it.displayName }
        )
    }

    /**
     * Read contacts from the device address book.
     * Returns pairs of (display name, phone number).
     */
    private suspend fun readDeviceContacts(): List<Pair<String, String>> {
        // TODO: Implement using ContentResolver for ContactsContract
        return emptyList()
    }

    /**
     * Hash a phone number using SHA-256 with a deterministic salt for
     * privacy-preserving discovery. The salt must match the iOS implementation
     * to ensure cross-platform contact discovery compatibility.
     */
    private fun hashPhoneNumber(phone: String): String {
        val normalized = phone.filter { it.isDigit() || it == '+' }
        val salt = "io.okaiwa.phone.salt.v1"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        digest.update(normalized.toByteArray(Charsets.UTF_8))
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Query the server for which phone hashes are registered.
     */
    private suspend fun queryRegisteredHashes(hashes: List<String>): Set<String> {
        // TODO: Implement API call in batches of 500
        return emptySet()
    }
}
