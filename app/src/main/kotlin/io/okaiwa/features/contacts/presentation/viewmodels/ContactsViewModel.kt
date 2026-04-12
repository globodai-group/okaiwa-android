package io.okaiwa.features.contacts.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.contacts.domain.entities.DeviceContact
import io.okaiwa.features.contacts.domain.repositories.DeviceContactsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the contact picker screen.
 */
data class ContactsUiState(
    val permissionState: PermissionState = PermissionState.Unknown,
    val isLoading: Boolean = false,
    val query: String = "",
    val allContacts: List<DeviceContact> = emptyList(),
) {
    private val filtered: List<DeviceContact>
        get() = if (query.isBlank()) allContacts
        else allContacts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.phoneNumberE164.contains(query.filter { c -> c.isDigit() || c == '+' })
        }

    val okaiwaContacts: List<DeviceContact>
        get() = filtered.filter { it.isOnOkaiwa }

    val inviteContacts: List<DeviceContact>
        get() = filtered.filter { !it.isOnOkaiwa }
}

enum class PermissionState { Unknown, Granted, Denied }

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsRepository: DeviceContactsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    /** Called by the screen when the system permission dialog resolves. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _uiState.update { it.copy(permissionState = PermissionState.Granted, isLoading = true) }
            loadContacts()
        } else {
            _uiState.update { it.copy(permissionState = PermissionState.Denied, isLoading = false) }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    private fun loadContacts() {
        viewModelScope.launch {
            val contacts = runCatching { contactsRepository.loadContacts() }.getOrDefault(emptyList())
            _uiState.update { it.copy(allContacts = contacts, isLoading = false) }
        }
    }
}
