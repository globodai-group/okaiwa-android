package io.okaiwa.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.okaiwa.core.theme.OkaiwaColors

/**
 * Primary surfaces of the app, selected via the bottom navigation.
 *
 * The order matters — it drives the visual order of the tab bar and
 * matches the iOS TabView order so users moving between platforms see
 * identical placement.
 */
enum class MainTab(
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
) {
    Chats(
        label = "Échanges",
        iconSelected = Icons.Filled.ChatBubble,
        iconUnselected = Icons.Outlined.ChatBubbleOutline,
    ),
    Wallet(
        label = "Wallet",
        iconSelected = Icons.Filled.AccountBalanceWallet,
        iconUnselected = Icons.Outlined.AccountBalanceWallet,
    ),
    Settings(
        label = "Paramètres",
        iconSelected = Icons.Filled.Settings,
        iconUnselected = Icons.Outlined.Settings,
    ),
    Profile(
        label = "Profil",
        iconSelected = Icons.Filled.Person,
        iconUnselected = Icons.Outlined.PersonOutline,
    ),
}

/**
 * Bottom-bar host that holds the four primary surfaces.
 *
 * The active tab's content is rendered via [content]; the caller wires
 * up the ConversationList / Wallet / Settings / Profile composables
 * based on the current [selectedTab].
 *
 * We render all tabs into the same Scaffold (no animated NavHost switch)
 * because switching the entire nav graph on tab taps would dismiss the
 * onscreen keyboard and snap scroll positions. A stateful host keeps
 * each surface's state alive across tab switches.
 */
@Composable
fun MainScaffold(
    content: @Composable (MainTab) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Chats) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content(selectedTab)
        }
        OkaiwaBottomBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )
    }
}

@Composable
private fun OkaiwaBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    Column {
        // Hairline divider — cheaper than a full elevation shadow, and
        // reads better on the dark canvas.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(OkaiwaColors.BlackBorder),
        )
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OkaiwaColors.Black)
                .navigationBarsPadding()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    tab: MainTab,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (isSelected) OkaiwaColors.Lime else OkaiwaColors.Muted
    val labelColor = if (isSelected) OkaiwaColors.Lime else OkaiwaColors.Muted

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isSelected) tab.iconSelected else tab.iconUnselected,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tab.label,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
