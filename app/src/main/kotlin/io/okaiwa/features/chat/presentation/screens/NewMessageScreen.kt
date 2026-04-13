package io.okaiwa.features.chat.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.R
import io.okaiwa.core.theme.OkaiwaColors
import io.okaiwa.features.chat.presentation.viewmodels.ConversationListViewModel
import io.okaiwa.features.discovery.presentation.DiscoverySearchViewModel
import java.util.concurrent.TimeUnit

/**
 * "Nouveau message" — the FAB destination on the Échanges tab.
 *
 * Mirrors Telegram's compose entry: three utility actions at the top
 * (new group, new channel, invite) followed by the list of Okaiwa
 * contacts the user can start a 1:1 conversation with.
 *
 * This screen does NOT request the system contacts permission. The
 * list is sourced from conversations we've already had (hence already
 * Okaiwa users). The device address book lives behind the separate
 * "Inviter un contact" action and the top-right contacts icon on the
 * conversation list, which push [ContactPickerScreen] instead.
 *
 * Groups and channels are placeholders for now — the multi-recipient
 * flow lands after the relay + Signal Protocol wiring. The rows are
 * rendered so the UX can be reviewed.
 */
@Composable
fun NewMessageScreen(
    onBack: () -> Unit,
    onStartConversation: (conversationId: String) -> Unit,
    onInviteContact: () -> Unit,
    onCreateGroup: () -> Unit = {},
    onCreateChannel: () -> Unit = {},
    viewModel: ConversationListViewModel = hiltViewModel(),
    discoveryVm: DiscoverySearchViewModel = hiltViewModel(),
) {
    val conversationsState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    val discoveryState by discoveryVm.state.collectAsState()

    // Flatten the 1:1 conversations into an Okaiwa contact list so the
    // user can start a fresh thread without opening the existing one.
    // Filter by the search query on both display name and username
    // (we don't have usernames yet — they'll plug in once identity
    // service ships; for now the mock surfaces the participant only).
    val contacts = remember(conversationsState.conversations, query) {
        conversationsState.conversations
            .flatMap { conv ->
                conv.participants.map { participant ->
                    OkaiwaContactRow(
                        conversationId = conv.id,
                        displayName = participant.displayName,
                        username = null,
                        updatedAtMs = conv.updatedAt,
                    )
                }
            }
            .distinctBy { it.conversationId }
            .filter { row ->
                if (query.isBlank()) true
                else row.displayName.contains(query, ignoreCase = true)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(onBack = onBack)
        SearchField(
            value = query,
            onValueChange = {
                query = it
                discoveryVm.onQueryChanged(it)
            },
        )

        // Real-time discovery — when the user types a username (3+
        // chars), the backend's GET /v1/discovery/username/:username is
        // hit after a 300 ms idle window. Match → "Démarrer" pill row.
        // No match → an explicit "Aucun utilisateur" line so the user
        // knows to invite the contact instead.
        DiscoveryResultRow(
            state = discoveryState,
            onStartConversation = { user ->
                // Persist the conversation row (peerDeviceId + identity
                // key + registration id) through the chat repository,
                // then navigate to the chat screen with the fresh id.
                discoveryVm.startConversation(user) { conversationId ->
                    onStartConversation(conversationId)
                }
            },
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                UtilityActionRow(
                    icon = Icons.Default.GroupAdd,
                    title = stringResource(R.string.new_message_new_group),
                    trailing = stringResource(R.string.new_message_new_group_trailing),
                    onClick = onCreateGroup,
                )
            }
            item {
                UtilityActionRow(
                    icon = Icons.Default.Campaign,
                    title = stringResource(R.string.new_message_new_channel),
                    trailing = stringResource(R.string.new_message_new_channel_badge),
                    trailingIsBadge = true,
                    onClick = onCreateChannel,
                )
            }
            item {
                UtilityActionRow(
                    icon = Icons.Default.PersonAdd,
                    title = stringResource(R.string.new_message_invite_contact),
                    trailing = null,
                    onClick = onInviteContact,
                )
            }
            item { SectionHeader(stringResource(R.string.new_message_section_sort_by_presence)) }

            items(contacts, key = { it.conversationId }) { row ->
                ContactRow(
                    row = row,
                    onClick = { onStartConversation(row.conversationId) },
                )
            }
        }
    }
}

private data class OkaiwaContactRow(
    val conversationId: String,
    val displayName: String,
    val username: String?,
    /**
     * Raw `updatedAt` of the backing conversation. The row composable
     * formats it into a localized "en ligne / last seen X ago" label via
     * [lastSeenLabel] — keeping the raw timestamp in the model means the
     * label follows the locale switch without a second data pass.
     */
    val updatedAtMs: Long,
) {
    val initial: String get() = displayName.trim().firstOrNull()?.uppercase() ?: "?"
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = OkaiwaColors.White,
            )
        }
        Text(
            text = stringResource(R.string.new_message_title),
            color = OkaiwaColors.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.new_message_search_placeholder), color = OkaiwaColors.Placeholder) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = OkaiwaColors.Muted)
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = TextStyle(color = OkaiwaColors.White, fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OkaiwaColors.Lime,
            unfocusedBorderColor = OkaiwaColors.BlackBorder,
            cursorColor = OkaiwaColors.Lime,
            focusedContainerColor = OkaiwaColors.BlackElevated,
            unfocusedContainerColor = OkaiwaColors.BlackElevated,
        ),
    )
}

@Composable
private fun UtilityActionRow(
    icon: ImageVector,
    title: String,
    trailing: String?,
    trailingIsBadge: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OkaiwaColors.Lime.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OkaiwaColors.Lime,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = OkaiwaColors.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            if (trailingIsBadge) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OkaiwaColors.Lime.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = trailing,
                        color = OkaiwaColors.Lime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(
                    text = trailing,
                    color = OkaiwaColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = title.uppercase(),
            color = OkaiwaColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ContactRow(
    row: OkaiwaContactRow,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OkaiwaColors.Lime.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = row.initial,
                color = OkaiwaColors.Lime,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.displayName,
                color = OkaiwaColors.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = lastSeenLabel(row.updatedAtMs),
                color = OkaiwaColors.Muted,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Localized "last seen" label derived from a conversation's updatedAt.
 * The composable wrapper lets each branch resolve its own
 * `stringResource(...)`, so the line re-renders in the right language
 * the moment the user flips the locale preference.
 */
@Composable
private fun lastSeenLabel(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 2 -> stringResource(R.string.new_message_presence_online)
        minutes < 60 -> stringResource(R.string.new_message_presence_online_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.new_message_presence_online_hours_ago, hours.toInt())
        days < 7 -> stringResource(R.string.new_message_presence_seen_days_ago, days.toInt())
        else -> stringResource(R.string.new_message_presence_seen_recently)
    }
}

@Composable
private fun DiscoveryResultRow(
    state: DiscoverySearchViewModel.State,
    onStartConversation: (io.okaiwa.features.discovery.data.remote.DiscoveredUser) -> Unit,
) {
    when (state) {
        DiscoverySearchViewModel.State.Idle -> Unit

        DiscoverySearchViewModel.State.Searching -> {
            Text(
                text = stringResource(R.string.new_message_discovery_searching),
                color = OkaiwaColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        DiscoverySearchViewModel.State.NotFound -> {
            Text(
                text = stringResource(R.string.new_message_discovery_not_found),
                color = OkaiwaColors.Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        is DiscoverySearchViewModel.State.Error -> {
            Text(
                text = state.message,
                color = OkaiwaColors.Error,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        is DiscoverySearchViewModel.State.Found -> {
            val user = state.user
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartConversation(user) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(OkaiwaColors.Lime.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = user.username?.firstOrNull()?.uppercase() ?: "@",
                        color = OkaiwaColors.Lime,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.profile?.displayName ?: user.username.orEmpty(),
                        color = OkaiwaColors.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "@${user.username.orEmpty()}",
                        color = OkaiwaColors.Muted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = stringResource(R.string.new_message_discovery_start_button),
                    color = OkaiwaColors.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OkaiwaColors.Lime)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}
