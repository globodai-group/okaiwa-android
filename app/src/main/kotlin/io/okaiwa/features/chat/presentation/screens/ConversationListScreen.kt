package io.okaiwa.features.chat.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.core.navigation.LocalFloatingBarPadding
import io.okaiwa.core.theme.OkaiwaColors
import io.okaiwa.features.chat.domain.entities.Conversation
import io.okaiwa.features.chat.presentation.viewmodels.ConversationListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Conversation list — the "Échanges" tab.
 *
 * Layout:
 *   - Top bar: "Okaiwa" wordmark, search, contacts icon (top-right).
 *   - List or empty state.
 *   - Floating "new conversation" FAB pinned above the tab bar.
 *
 * The FAB is positioned manually rather than through Scaffold's
 * `floatingActionButton` slot because the app's main bottom bar isn't a
 * Scaffold bottomBar — it floats over every tab via `MainScaffold`. The
 * Scaffold-owned FAB would therefore sit at the screen edge, underneath
 * the floating nav. Positioning it manually lets us read the
 * `LocalFloatingBarPadding` CompositionLocal and offset the FAB by the
 * bar's reserved height plus a gutter.
 */
@Composable
fun ConversationListScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToContacts: () -> Unit = {},
    onNavigateToNewConversation: () -> Unit = onNavigateToContacts,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val floatingBarInset = LocalFloatingBarPadding.current.calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ConversationListTopBar(
                onSearchClick = { /* TODO: inline search */ },
                onContactsClick = onNavigateToContacts,
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = floatingBarInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = OkaiwaColors.Lime)
                    }
                }

                uiState.conversations.isEmpty() -> {
                    EmptyConversationsView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = floatingBarInset),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = floatingBarInset),
                    ) {
                        val pinned = uiState.pinnedConversations
                        if (pinned.isNotEmpty()) {
                            items(items = pinned, key = { it.id }) { conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    onClick = {
                                        viewModel.markConversationAsRead(conversation.id)
                                        onNavigateToChat(conversation.id)
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 76.dp),
                                    color = OkaiwaColors.BlackBorder,
                                )
                            }
                        }
                        items(items = uiState.regularConversations, key = { it.id }) { conversation ->
                            ConversationItem(
                                conversation = conversation,
                                onClick = {
                                    viewModel.markConversationAsRead(conversation.id)
                                    onNavigateToChat(conversation.id)
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = OkaiwaColors.BlackBorder,
                            )
                        }
                    }
                }
            }
        }

        // FAB — rounded square lime tile above the floating bar. The
        // default Material 3 FAB shape is a 16 dp rounded square, which
        // matches the language of every other surface (Welcome CTAs,
        // floating nav bar itself). The earlier circle + pencil icon
        // drifted from that language; a chat bubble reads more directly
        // as "new conversation".
        FloatingActionButton(
            onClick = onNavigateToNewConversation,
            containerColor = OkaiwaColors.Lime,
            contentColor = OkaiwaColors.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // 16 dp gutter above the floating bar keeps the FAB
                // comfortably off the bar rather than hugging it.
                .padding(end = 20.dp, bottom = floatingBarInset + 16.dp)
                .size(56.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Nouvelle conversation",
            )
        }
    }
}

@Composable
private fun ConversationListTopBar(
    onSearchClick: () -> Unit,
    onContactsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Okaiwa",
            color = OkaiwaColors.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Rechercher",
                tint = OkaiwaColors.White,
            )
        }
        IconButton(onClick = onContactsClick) {
            Icon(
                imageVector = Icons.Outlined.People,
                contentDescription = "Contacts",
                tint = OkaiwaColors.White,
            )
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            color = OkaiwaColors.Lime.copy(alpha = 0.15f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = conversation.displayTitle.take(1).uppercase(),
                    color = OkaiwaColors.Lime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversation.displayTitle,
                    color = OkaiwaColors.White,
                    fontSize = 16.sp,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                conversation.lastMessage?.let { preview ->
                    Text(
                        text = formatTimestamp(preview.timestamp),
                        fontSize = 12.sp,
                        color = if (conversation.unreadCount > 0) OkaiwaColors.Lime else OkaiwaColors.Muted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversation.lastMessage?.content ?: "",
                    fontSize = 14.sp,
                    color = OkaiwaColors.WhiteDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = OkaiwaColors.Lime,
                        contentColor = OkaiwaColors.Black,
                    ) {
                        Text(
                            text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Chat,
            contentDescription = null,
            tint = OkaiwaColors.Muted,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Aucune conversation",
            color = OkaiwaColors.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Commencez une conversation chiffrée avec un contact.",
            color = OkaiwaColors.Muted,
            fontSize = 14.sp,
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val dayMs = 24 * 60 * 60 * 1000L

    return when {
        diff < dayMs -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 7 * dayMs -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}
