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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val floatingBarInset = LocalFloatingBarPadding.current.calculateBottomPadding()
    var selectedTab by remember { mutableStateOf(PublicationTab.Active) }

    val profile = uiState.profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding(),
    ) {
        TopBar(
            onQrClick = onOpenQrCode,
            onMoreClick = { /* TODO: action sheet (logout, delete account) */ },
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
private fun TopBar(onQrClick: () -> Unit, onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onQrClick) {
            Icon(Icons.Default.QrCode, contentDescription = "QR code", tint = OkaiwaColors.White)
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onMoreClick) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "Plus", tint = OkaiwaColors.White)
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
                    contentDescription = "Compte vérifié Pro",
                    tint = OkaiwaColors.Lime,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (profile.isOnline) "en ligne" else "hors ligne",
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
            label = "Photo",
            onClick = onPickAvatar,
        )
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Edit,
            label = "Modifier",
            onClick = onEditProfile,
        )
        QuickActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Settings,
            label = "Paramètres",
            onClick = onOpenSettings,
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
        InfoRow(label = "Mobile", value = profile.phoneNumberE164)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(OkaiwaColors.BlackBorder),
        )
        InfoRow(label = "Nom d'utilisateur", value = profile.username)
        if (!profile.bio.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(OkaiwaColors.BlackBorder),
            )
            InfoRow(label = "Bio", value = profile.bio)
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
            text = "Publications",
            isSelected = selected == PublicationTab.Active,
            onClick = { onSelected(PublicationTab.Active) },
        )
        TabPill(
            text = "Publications archivées",
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
            text = "Aucune publication...",
            color = OkaiwaColors.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Publiez des photos et vidéos à afficher sur votre page de profil.",
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
                text = "Ajouter une publication",
                color = OkaiwaColors.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}
