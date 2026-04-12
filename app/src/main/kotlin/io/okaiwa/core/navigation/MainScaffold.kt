package io.okaiwa.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.okaiwa.core.theme.OkaiwaColors

/** Primary surfaces of the app, selected via the floating bottom bar. */
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
 * Vertical padding the floating bar needs from the bottom edge of the
 * screen. Tabs expose it to their content via [LocalFloatingBarPadding]
 * so `LazyColumn` content-padding can reserve room and the last item
 * doesn't sit behind the bar.
 */
private val FloatingBarHeight = 64.dp
private val FloatingBarBottomMargin = 12.dp
private val FloatingBarHorizontalMargin = 16.dp

/**
 * [PaddingValues] the current tab should add to its scrollable content
 * so nothing lives permanently behind the floating bar. Tabs read this
 * via `LocalFloatingBarPadding.current`.
 */
val LocalFloatingBarPadding = staticCompositionLocalOf { PaddingValues(bottom = 0.dp) }

/**
 * Bottom-bar host that holds the four primary surfaces.
 *
 * The bar floats over the tab content rather than pushing it up — this
 * lets lists scroll through the translucent bar (Telegram-style) while
 * preserving the brand's soft-rounded corner language instead of the
 * full pill shape. Corner radius is tied to the main button radius so
 * the bar reads as a big sibling of the Welcome CTAs.
 */
@Composable
fun MainScaffold(
    content: @Composable (MainTab) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Chats) }

    // Exposes the required content inset for the active tab so its
    // scrollable content can avoid the translucent bar overlay.
    val tabContentPadding = PaddingValues(
        bottom = FloatingBarHeight + FloatingBarBottomMargin + 16.dp,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black),
    ) {
        CompositionLocalProvider(LocalFloatingBarPadding provides tabContentPadding) {
            content(selectedTab)
        }
        FloatingNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun FloatingNavBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(
                start = FloatingBarHorizontalMargin,
                end = FloatingBarHorizontalMargin,
                bottom = FloatingBarBottomMargin,
            )
            .fillMaxWidth()
            .height(FloatingBarHeight)
            // Soft lift — reads as "floating" over the content without
            // the harsh shadow the default elevation would paint.
            .shadow(
                elevation = 12.dp,
                shape = barShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(barShape)
            // 92 % opacity over the elevated canvas keeps a subtle hint
            // of the list content behind the bar (Telegram-style) while
            // staying solid enough that tab labels never sit on top of
            // noisy artwork. Tuned after the 78 % version read as too
            // washed-out on device.
            .background(OkaiwaColors.BlackElevated.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = OkaiwaColors.BlackBorder.copy(alpha = 0.7f),
                shape = barShape,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
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
private fun RowScope.TabItem(
    tab: MainTab,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (isSelected) OkaiwaColors.Lime else OkaiwaColors.Muted
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                // Drop the default ripple: on a translucent bar the ripple
                // paints a lime rectangle under the tab that reads as a
                // stuck selection state on the screenshot.
                interactionSource = interactionSource,
                indication = null,
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
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
