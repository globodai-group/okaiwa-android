package io.okaiwa.features.chat.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.chat.domain.entities.Conversation
import io.okaiwa.features.chat.domain.entities.Message
import io.okaiwa.features.chat.domain.repositories.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for a single conversation view.
 *
 * Reads `conversationId` from the navigation backstack via
 * [SavedStateHandle], observes the repository message stream, and
 * exposes the messages plus the peer's display name for the top bar.
 */
data class ChatUiState(
    val conversationId: String = "",
    val title: String = "Conversation",
    val isVerified: Boolean = false,
    val messages: List<Message> = emptyList(),
    val currentUserId: String = "me",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""

    private val _uiState = MutableStateFlow(ChatUiState(conversationId = conversationId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeConversationMetadata()
        observeMessages()
    }

    private fun observeConversationMetadata() {
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                val conv: Conversation? = conversations.firstOrNull { it.id == conversationId }
                if (conv != null) {
                    _uiState.update {
                        it.copy(
                            title = conv.displayTitle,
                            isVerified = conv.isFullyVerified,
                        )
                    }
                }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            runCatching { chatRepository.sendMessage(conversationId, content) }
        }
    }
}
