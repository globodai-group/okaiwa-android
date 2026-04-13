package io.okaiwa.features.profile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.R
import io.okaiwa.core.navigation.LocalFloatingBarPadding
import io.okaiwa.core.theme.OkaiwaColors
import io.okaiwa.features.profile.domain.entities.UserProfile
import io.okaiwa.features.profile.presentation.viewmodels.ProfileViewModel

/**
 * Profile tab — Telegram-style identity card.
 *
 * Layout, top to bottom:
 *   - Top bar: QR code icon (left), kebab menu (right).
 *   - Avatar medallion + display name + verified star + presence text.
 *   - Three quick-action tiles: Photo, Modifier, Paramètres.
 *   - Info card with phone + handle.
 *   - Publications tabs (Publications / Publications archivées) — the
 *     stories surface scoped for a later iteration.
 *   - Empty state with a "Ajouter une publication" CTA.
 *
 * The repository this view binds to is documented in `UserProfile.kt`
 * — read it before touching this screen so the field-level backend
 * surface is kept consistent.
 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit = {},
    onOpenQrCode: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onPickAvatar: () -> Unit = {},
    onAddPublication: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onOpenLanguagePicker: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val floatingBarInset = LocalFloatingBarPadding.current.calculateBottomPadding()
    var selectedTab by remember { mutableStateOf(PublicationTab.Active) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val profile = uiState.profile

    // Once signOut() flips the flag in the ViewModel, the navigation
    // layer pops back to Welcome. We immediately ack via
    // onSignOutNavigated() so the LaunchedEffect won't re-fire on
    // recomposition / back-navigation to a stale ProfileScreen — the
    // navigate call would otherwise stack a second Welcome route.
    LaunchedEffect(uiState.signedOut) {
        if (uiState.signedOut) {
            onSignedOut()
            viewModel.onSignOutNavigated()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding(),
    ) {
        TopBar(
            onQrClick = onOpenQrCode,
            onMoreClick = { showMoreMenu = true },
            isMoreMenuOpen = showMoreMenu,
            onMoreMenuDismiss = { showMoreMenu = false },
            onSignOut = {
                showMoreMenu = false
                viewModel.signOut()
            },
        )

        if (profile == null) {
            Spacer(modifier = Modifier.fillMaxSize())
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                AvatarBlock(profile = profile, onPickAvatar = onPickAvatar)
                Spacer(modifier = Modifier.height(20.dp))
                QuickActions(
                    onPickAvatar = onPickAvatar,
                    onEditProfile = onEditProfile,
                    onOpenSettings = onOpenSettings,
                    onOpenLanguagePicker = onOpenLanguagePicker,
                )
                Spacer(modifier = Modifier.height(20.dp))
                IdentityCard(profile = profile)
                Spacer(modifier = Modifier.height(20.dp))
                PublicationTabs(
                    selected = selectedTab,
                    onSelected = { selectedTab = it },
                )
                Spacer(modifier = Modifier.height(40.dp))
                EmptyPublications(onAdd = onAddPublication)
                Spacer(modifier = Modifier.height(floatingBarInset + 16.dp))
            }
        }
    }
}

@Composable
private fun TopBar(
    onQrClick: () -> Unit,
    onMoreClick: () -> Unit,
    isMoreMenuOpen: Boolean,
    onMoreMenuDismiss: () -> Unit,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onQrClick) {
            Icon(Icons.Default.QrCode, contentDescription = stringResource(R.string.profile_top_bar_qr_cd), tint = OkaiwaColors.White)
        }
        Spacer(modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.profile_top_bar_more_cd), tint = OkaiwaColors.White)
            }
            DropdownMenu(
                expanded = isMoreMenuOpen,
                onDismissRequest = onMoreMenuDismiss,
                // Anchor the dropdown to the kebab IconButton — the
                // default M3 placement nudges it to the start of the
                // anchor on phones, which keeps it under the icon
                // instead of bleeding off the right edge of the screen.
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.profile_more_menu_logout),
                            color = OkaiwaColors.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = OkaiwaColors.White,
                        )
                    },
                    onClick = onSignOut,
                )
            }
        }
    }
}

@Composable
private fun AvatarBlock(profile: UserProfile, onPickAvatar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(OkaiwaColors.Lime.copy(alpha = 0.18f))
                .border(2.dp, OkaiwaColors.BlackBorder, CircleShape)
                .clickable(onClick = onPickAvatar),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = profile.displayName.firstOrNull()?.uppercase() ?: "?",
                color = OkaiwaColors.Lime,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.displayName,
                color = OkaiwaColors.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            if (profile.isVerified) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.profile_verified_cd),
                    tint = OkaiwaColors.Lime,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(
                if (profile.isOnline) R.string.profile_presence_online
                else R.string.profile_presence_offline,
            ),
            color = if (profile.isOnline) OkaiwaColors.Lime else OkaiwaColors.Muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun QuickActions(
    onPickAvatar: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CameraAlt,
            label = stringResource(R.string.profile_quick_action_photo),
            onClick = onPickAvatar,
        )
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Edit,
            label = stringResource(R.string.profile_quick_action_edit),
            onClick = onEditProfile,
        )
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Settings,
            label = stringResource(R.string.profile_quick_action_settings),
            onClick = onOpenSettings,
        )
        // Language picker — surfaced here rather than buried in Settings
        // (which is itself a placeholder for now) so the French/English
        // switch is discoverable as soon as the profile tab opens.
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Language,
            label = stringResource(R.string.profile_language_row_label),
            onClick = onOpenLanguagePicker,
        )
    }
}

@Composable
private fun QuickActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(OkaiwaColors.BlackElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OkaiwaColors.Lime,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = OkaiwaColors.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IdentityCard(profile: UserProfile) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OkaiwaColors.BlackElevated),
    ) {
        InfoRow(label = stringResource(R.string.profile_info_mobile), value = profile.phoneNumberE164)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(OkaiwaColors.BlackBorder),
        )
        InfoRow(label = stringResource(R.string.profile_info_username), value = profile.username)
        if (!profile.bio.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(OkaiwaColors.BlackBorder),
            )
            InfoRow(label = stringResource(R.string.profile_info_bio), value = profile.bio)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(value, color = OkaiwaColors.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = OkaiwaColors.Muted, fontSize = 12.sp)
    }
}

private enum class PublicationTab { Active, Archived }

@Composable
private fun PublicationTabs(selected: PublicationTab, onSelected: (PublicationTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabPill(
            text = stringResource(R.string.profile_publications_active_tab),
            isSelected = selected == PublicationTab.Active,
            onClick = { onSelected(PublicationTab.Active) },
        )
        TabPill(
            text = stringResource(R.string.profile_publications_archived_tab),
            isSelected = selected == PublicationTab.Archived,
            onClick = { onSelected(PublicationTab.Archived) },
        )
    }
}

@Composable
private fun TabPill(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) OkaiwaColors.Lime.copy(alpha = 0.2f) else OkaiwaColors.BlackElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (isSelected) OkaiwaColors.Lime else OkaiwaColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyPublications(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.profile_publications_empty_title),
            color = OkaiwaColors.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.profile_publications_empty_subtitle),
            color = OkaiwaColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(OkaiwaColors.Lime)
                .clickable(onClick = onAdd)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = OkaiwaColors.Black,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.profile_publications_add_button),
                color = OkaiwaColors.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}
