package io.okaiwa.features.discovery.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.chat.domain.repositories.ChatRepository
import io.okaiwa.features.discovery.data.remote.DiscoveredUser
import io.okaiwa.features.discovery.data.remote.DiscoveryApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the inline search field on [NewMessageScreen]. The user types
 * a username (with or without leading `@`) and we hit
 * `GET /v1/discovery/username/:username` after a 300 ms idle window so
 * we don't spam the backend on every keystroke.
 *
 * Result states (visible to the screen):
 *   - Idle — empty query.
 *   - Searching — request in flight.
 *   - Found(user) — exact match.
 *   - NotFound — server returned 404.
 *   - Error(msg) — anything else.
 */
@HiltViewModel
class DiscoverySearchViewModel @Inject constructor(
    private val discoveryApi: DiscoveryApi,
    private val chatRepository: ChatRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Searching : State
        data class Found(val user: DiscoveredUser) : State
        data object NotFound : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var debounceJob: Job? = null

    /**
     * Feed a fresh query. The leading `@` is stripped because the
     * server-side validator wants raw alphanumeric + underscore.
     * Below 3 chars we stay idle (matches the backend minLength).
     */
    fun onQueryChanged(rawQuery: String) {
        val query = rawQuery.trim().removePrefix("@")
        debounceJob?.cancel()

        if (query.length < 3) {
            _state.value = State.Idle
            return
        }

        debounceJob = viewModelScope.launch {
            delay(300)
            _state.value = State.Searching
            val result = runCatching { discoveryApi.searchByUsername(query) }
            result.onSuccess { response ->
                _state.value = when {
                    response.code() == 404 -> State.NotFound
                    response.isSuccessful -> response.body()?.let { State.Found(it) }
                        ?: State.Error("Réponse vide du serveur")
                    else -> State.Error("Erreur ${response.code()}")
                }
            }.onFailure { t ->
                _state.value = State.Error(t.message ?: "Erreur réseau")
            }
        }
    }

    fun clear() {
        debounceJob?.cancel()
        _state.value = State.Idle
    }

    /**
     * Persist a conversation row for the discovery hit and invoke
     * [onReady] with the fresh (or pre-existing) conversation id. The
     * actual Signal session is established lazily on the first send —
     * see [io.okaiwa.features.chat.data.repositories.RemoteChatRepository.sendMessage].
     */
    fun startConversation(user: DiscoveredUser, onReady: (conversationId: String) -> Unit) {
        // Block the user-taps-own-handle case up front. libsignal
        // doesn't model a "note to self" conversation (a single
        // device trying to encrypt + decrypt against its own
        // identityKey would hang on its own ratchet advance), and
        // even if it did, the discovery → sendMessage path would
        // then 400 at the relay's recipientDeviceId==senderDeviceId
        // guard. Surfacing an explicit message here beats silently
        // failing deeper in the stack.
        val selfAccountId = sessionStore.current()?.accountId
        if (!selfAccountId.isNullOrEmpty() && selfAccountId == user.accountId) {
            _state.value = State.Error(
                "Vous ne pouvez pas démarrer une conversation avec votre propre compte",
            )
            return
        }
        viewModelScope.launch {
            runCatching { chatRepository.createConversationFromDiscovery(user) }
                .onSuccess { conv -> onReady(conv.id) }
                .onFailure { t ->
                    _state.value = State.Error(t.message ?: "Impossible de démarrer la conversation")
                }
        }
    }
}
