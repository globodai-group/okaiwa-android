package io.okaiwa.features.wallet.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.okaiwa.core.navigation.LocalFloatingBarPadding
import io.okaiwa.core.theme.OkaiwaColors

/**
 * Wallet tab entry state shown while the user has no wallet set up yet.
 *
 * Two primary actions, mirroring the top-level Welcome screen but scoped
 * to the crypto story:
 *   - "Créer un wallet"  → generates a fresh BIP-39 seed + lands in the
 *                           backup-method selection flow (seed phrase /
 *                           Google OAuth + Shamir 2-of-3 / master key).
 *   - "Importer un wallet" → recovers from an existing 12 / 24-word
 *                             mnemonic.
 *
 * The full onboarding steps are placeholder-backed for now — the real
 * BIP-39 seed generation and SSS backup land once the wallet-core FFI
 * is linked in. This screen deliberately avoids the false-positive
 * "empty wallet" overview (balance 0, no transactions) the old Wallet
 * tab showed even before any keys existed.
 */
@Composable
fun WalletOnboardingScreen(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit,
) {
    val floatingBarInset = LocalFloatingBarPadding.current.calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Wallet",
            color = OkaiwaColors.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Start,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(bottom = 120.dp),
            ) {
                // Rounded lime medallion with wallet glyph.
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(OkaiwaColors.Lime.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = OkaiwaColors.Lime,
                        modifier = Modifier.size(44.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Votre wallet crypto",
                    color = OkaiwaColors.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Envoyez et recevez des cryptos directement dans vos conversations. Vos clés restent sur votre appareil.",
                    color = OkaiwaColors.WhiteDim,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    // Sit above the floating tab bar with an extra 16 dp
                    // gutter so the lime button breathes. The bar inset
                    // alone left the CTAs visually kissing the bar.
                    .padding(bottom = floatingBarInset + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onCreateWallet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OkaiwaColors.Lime,
                        contentColor = OkaiwaColors.Black,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "Créer un wallet",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }

                OutlinedButton(
                    onClick = onImportWallet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, OkaiwaColors.Lime),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OkaiwaColors.Lime,
                        containerColor = Color.Transparent,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "Importer un wallet",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
