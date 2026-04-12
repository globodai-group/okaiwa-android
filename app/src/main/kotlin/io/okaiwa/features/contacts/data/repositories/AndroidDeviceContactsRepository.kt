package io.okaiwa.features.contacts.data.repositories

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.okaiwa.features.contacts.domain.entities.DeviceContact
import io.okaiwa.features.contacts.domain.repositories.DeviceContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads contacts from Android's `ContactsContract` after the user has
 * granted the `READ_CONTACTS` permission. The permission check + request
 * happens in the Compose layer; this repository is purely read-side.
 *
 * For the beta, a deterministic subset of phone-number hashes is flagged
 * as "on Okaiwa" so the UI split (message vs invite) has real content
 * without the identity service being wired up yet. A real sync will
 * replace this coin-flip with a PBKDF2 + identity-service lookup.
 */
@Singleton
class AndroidDeviceContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceContactsRepository {

    override suspend fun loadContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext emptyList()

        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val sort = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"

        val results = mutableListOf<DeviceContact>()
        val seen = HashSet<String>()

        resolver.query(uri, projection, null, null, sort)?.use { cursor ->
            val idIx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIx)?.trim().orEmpty()
                val rawNumber = cursor.getString(numberIx)?.trim().orEmpty()
                if (name.isEmpty() || rawNumber.isEmpty()) continue

                val normalized = rawNumber.filter { it.isDigit() || it == '+' }
                if (!seen.add("$name:$normalized")) continue

                val id = cursor.getLong(idIx).toString()
                val photoUri = cursor.getString(photoIx)

                results.add(
                    DeviceContact(
                        id = id,
                        displayName = name,
                        phoneNumberE164 = normalized,
                        avatarUri = photoUri,
                        // Mock "on Okaiwa" flag — every third contact
                        // is marked as on the platform so we can test
                        // both the message-row and invite-row UX.
                        isOnOkaiwa = (normalized.hashCode() and 3) == 0,
                        okaiwaUsername = if ((normalized.hashCode() and 3) == 0) {
                            "@${name.lowercase().replace(" ", "").take(12)}"
                        } else null,
                    ),
                )
            }
        }

        results
    }

    private fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
}
