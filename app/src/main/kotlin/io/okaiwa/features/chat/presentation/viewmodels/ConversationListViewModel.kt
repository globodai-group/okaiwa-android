package io.okaiwa.features.chat.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.chat.domain.entities.Conversation
import io.okaiwa.features.chat.domain.repositories.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the conversation list screen.
 */
data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
) {
    /**
     * Filtered conversations based on search query.
     */
    val filteredConversations: List<Conversation>
        get() = if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter { conversation ->
                conversation.displayTitle.contains(searchQuery, ignoreCase = true) ||
                    conversation.lastMessage?.content?.contains(searchQuery, ignoreCase = true) == true
            }
        }

    /**
     * Total unread count across all conversations.
     */
    val totalUnreadCount: Int
        get() = conversations.sumOf { it.unreadCount }

    /**
     * Pinned conversations displayed at the top.
     */
    val pinnedConversations: List<Conversation>
        get() = filteredConversations.filter { it.isPinned }

    /**
     * Non-pinned, non-archived conversations.
     */
    val regularConversations: List<Conversation>
        get() = filteredConversations.filter { !it.isPinned && !it.isArchived }
}

/**
 * ViewModel for the conversation list screen.
 *
 * Observes conversations from the repository as a Flow and exposes
 * them as StateFlow for the Compose UI. Supports search filtering,
 * archiving, and muting.
 */
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        observeConversations()
    }

    private fun observeConversations() {
        viewModelScope.launch {
            chatRepository.observeConversations()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load conversations",
                        )
                    }
                }
                .collect { conversations ->
                    _uiState.update {
                        it.copy(
                            conversations = conversations,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun markConversationAsRead(conversationId: String) {
        viewModelScope.launch {
            runCatching { chatRepository.markAsRead(conversationId) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
