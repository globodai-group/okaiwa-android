package io.okaiwa.features.settings.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.okaiwa.R
import io.okaiwa.features.settings.domain.entities.SecurityScore

/**
 * Settings screen.
 *
 * Displays security score, account settings, privacy options, and app
 * configuration. Organized in sections with Material 3 styling.
 */
@Composable
fun SettingsScreen() {
    // Placeholder security score — in production, comes from ViewModel
    val securityScore = SecurityScore(
        hasDevicePasscodeEnabled = true,
        hasBiometricEnabled = true,
        hasVerifiedContacts = false,
        hasDisappearingMessages = false,
        hasPreKeysUpToDate = true,
        hasScreenSecurityEnabled = true,
        hasUpdatedApp = true,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_top_bar_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Security score card
            SecurityScoreCard(securityScore = securityScore)

            // Account section
            SettingsSection(title = stringResource(R.string.settings_section_account)) {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.settings_item_profile_title),
                    subtitle = stringResource(R.string.settings_item_profile_subtitle),
                    onClick = { /* TODO */ },
                )
                SettingsItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = stringResource(R.string.settings_item_wallet_title),
                    subtitle = stringResource(R.string.settings_item_wallet_subtitle),
                    onClick = { /* TODO */ },
                )
            }

            // Privacy section
            SettingsSection(title = stringResource(R.string.settings_section_privacy)) {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.settings_item_privacy_title),
                    subtitle = stringResource(R.string.settings_item_privacy_subtitle),
                    onClick = { /* TODO */ },
                )
                SettingsItem(
                    icon = Icons.Default.Fingerprint,
                    title = stringResource(R.string.settings_item_biometric_title),
                    subtitle = stringResource(R.string.settings_item_biometric_subtitle),
                    onClick = { /* TODO */ },
                )
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.settings_item_screen_security_title),
                    subtitle = stringResource(R.string.settings_item_screen_security_subtitle),
                    onClick = { /* TODO */ },
                )
            }

            // App section
            SettingsSection(title = stringResource(R.string.settings_section_app)) {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = stringResource(R.string.settings_item_chats_title),
                    subtitle = stringResource(R.string.settings_item_chats_subtitle),
                    onClick = { /* TODO */ },
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_item_notifications_title),
                    subtitle = stringResource(R.string.settings_item_notifications_subtitle),
                    onClick = { /* TODO */ },
                )
                SettingsItem(
                    icon = Icons.Default.ColorLens,
                    title = stringResource(R.string.settings_item_appearance_title),
                    subtitle = stringResource(R.string.settings_item_appearance_subtitle),
                    onClick = { /* TODO */ },
                )
            }

            // About section — pulls the version string generated by
            // Gradle from `git rev-list --count HEAD`, so this line
            // tracks the build shipped to the tester without anyone
            // having to hand-edit it.
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_item_about_title),
                    subtitle = stringResource(
                        R.string.settings_item_about_subtitle,
                        io.okaiwa.BuildConfig.VERSION_NAME,
                        io.okaiwa.BuildConfig.VERSION_CODE,
                    ),
                    onClick = { /* TODO */ },
                )
            }

            // Danger zone
            SettingsItem(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.settings_item_delete_account_title),
                subtitle = stringResource(R.string.settings_item_delete_account_subtitle),
                onClick = { /* TODO: confirmation dialog */ },
                isDanger = true,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SecurityScoreCard(securityScore: SecurityScore) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_security_score_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Text(
                    text = "${securityScore.percentage}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { securityScore.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.settings_security_score_progress,
                    securityScore.level.label,
                    securityScore.metCount,
                    securityScore.totalCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDanger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!isDanger) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
