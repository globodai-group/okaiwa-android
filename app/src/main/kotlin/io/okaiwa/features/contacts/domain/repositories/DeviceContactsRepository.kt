package io.okaiwa.features.contacts.domain.repositories

import io.okaiwa.features.contacts.domain.entities.DeviceContact

/**
 * Device address-book access.
 *
 * Implementations read from `ContactsContract` on Android and
 * `CNContactStore` on iOS. Callers are expected to hold the
 * corresponding runtime permission before calling [loadContacts];
 * without the permission, implementations return an empty list.
 */
interface DeviceContactsRepository {
    suspend fun loadContacts(): List<DeviceContact>
}
