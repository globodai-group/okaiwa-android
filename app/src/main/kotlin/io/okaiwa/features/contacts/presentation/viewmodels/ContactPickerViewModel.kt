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

data class ContactPickerUiState(
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val query: String = "",
    val contacts: List<DeviceContact> = emptyList(),
) {
    val filtered: List<DeviceContact>
        get() = if (query.isBlank()) contacts else contacts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.phoneNumberE164.contains(query)
        }

    val onOkaiwa: List<DeviceContact>
        get() = filtered.filter { it.isOnOkaiwa }

    val toInvite: List<DeviceContact>
        get() = filtered.filter { !it.isOnOkaiwa }
}

@HiltViewModel
class ContactPickerViewModel @Inject constructor(
    private val repository: DeviceContactsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactPickerUiState())
    val uiState: StateFlow<ContactPickerUiState> = _uiState.asStateFlow()

    fun onPermissionGranted() {
        _uiState.update { it.copy(hasPermission = true, isLoading = true) }
        viewModelScope.launch {
            val contacts = repository.loadContacts()
            _uiState.update { it.copy(contacts = contacts, isLoading = false) }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(hasPermission = false, isLoading = false) }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }
}
