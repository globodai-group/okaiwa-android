package io.okaiwa.features.wallet.presentation.onboarding

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.R
import io.okaiwa.core.theme.OkaiwaColors
import kotlinx.coroutines.launch

/**
 * "Créer un portefeuille" onboarding — six-step state machine.
 *
 * The flow lives inside a single composable so the throwaway steps
 * don't pollute the NavHost. Step transitions go through the shared
 * [WalletOnboardingViewModel], and the caller only sees two events:
 * `onFinished` when the user exits successfully, `onCancel` when they
 * back out of step 1.
 */
@Composable
fun WalletOnboardingFlow(
    onFinished: () -> Unit,
    onCancel: () -> Unit,
    viewModel: WalletOnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        when (uiState.step) {
            WalletOnboardingStep.Method -> MethodStep(
                onPickSeedPhrase = { viewModel.selectMethod(WalletCreationMethod.SeedPhrase) },
                onPickPasskey = { viewModel.selectMethod(WalletCreationMethod.Passkey) },
                onCancel = onCancel,
            )

            WalletOnboardingStep.SecurityTips -> SecurityTipsStep(
                method = uiState.method,
                onAccept = { viewModel.onSecurityTipsAccepted() },
                onBack = { if (!viewModel.previousStep()) onCancel() },
            )

            WalletOnboardingStep.SeedPhraseDisplay -> SeedPhraseDisplayStep(
                mnemonic = uiState.mnemonic,
                savedToPasswordManager = uiState.savedToPasswordManager,
                onSaved = viewModel::markSavedToPasswordManager,
                onContinue = viewModel::onSeedPhraseAcknowledged,
                onBack = { viewModel.previousStep() },
            )

            WalletOnboardingStep.SeedPhraseVerify -> SeedPhraseVerifyStep(
                mnemonic = uiState.mnemonic,
                verifyIndices = uiState.verifyIndices,
                onSuccess = viewModel::onVerificationSuccess,
                onBack = { viewModel.previousStep() },
            )

            WalletOnboardingStep.PasskeyCreation -> PasskeyCreationStep(
                onCreated = viewModel::onPasskeyCreated,
                onBack = { viewModel.previousStep() },
            )

            WalletOnboardingStep.NameWallet -> NameWalletStep(
                name = uiState.walletName,
                onNameChanged = viewModel::setWalletName,
                onConfirm = viewModel::confirmName,
                onBack = { viewModel.previousStep() },
            )

            WalletOnboardingStep.Ready -> ReadyStep(
                walletName = uiState.walletName.ifBlank { stringResource(R.string.wallet_create_default_name) },
                onDismiss = onFinished,
                onFundWallet = onFinished,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Step 1 — Method choice
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun MethodStep(
    onPickSeedPhrase: () -> Unit,
    onPickPasskey: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.wallet_create_step_method_title), onBack = onCancel)

        Spacer(modifier = Modifier.height(8.dp))

        MethodCard(
            icon = Icons.Default.LockReset,
            title = stringResource(R.string.wallet_create_method_seed_title),
            subtitle = stringResource(R.string.wallet_create_method_seed_subtitle),
            badge = stringResource(R.string.wallet_create_method_seed_badge),
            bodyLines = listOf(
                stringResource(R.string.wallet_create_method_seed_body_1),
                stringResource(R.string.wallet_create_method_seed_body_2),
                stringResource(R.string.wallet_create_method_seed_body_3),
            ),
            ctaLabel = stringResource(R.string.wallet_create_method_cta),
            onClick = onPickSeedPhrase,
            isPrimary = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        MethodCard(
            icon = Icons.Default.Fingerprint,
            title = stringResource(R.string.wallet_create_method_passkey_title),
            subtitle = stringResource(R.string.wallet_create_method_passkey_subtitle),
            badge = stringResource(R.string.wallet_create_method_passkey_badge),
            bodyLines = listOf(
                stringResource(R.string.wallet_create_method_passkey_body_1),
                stringResource(R.string.wallet_create_method_passkey_body_2),
                stringResource(R.string.wallet_create_method_passkey_body_3),
            ),
            ctaLabel = stringResource(R.string.wallet_create_method_cta),
            onClick = onPickPasskey,
            isPrimary = false,
        )
    }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    bodyLines: List<String>,
    ctaLabel: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OkaiwaColors.BlackElevated)
            .padding(16.dp),
    ) {
        if (isPrimary) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(OkaiwaColors.Lime.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = badge,
                    color = OkaiwaColors.Lime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(OkaiwaColors.Lime.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = OkaiwaColors.Lime, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = OkaiwaColors.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!isPrimary) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(OkaiwaColors.Muted.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(badge, color = OkaiwaColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(subtitle, color = OkaiwaColors.Muted, fontSize = 12.sp)
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OkaiwaColors.Lime,
                    contentColor = OkaiwaColors.Black,
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Text(ctaLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        bodyLines.forEach { line ->
            Text(
                text = line,
                color = OkaiwaColors.WhiteDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Step 2 — Security tips
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun SecurityTipsStep(
    method: WalletCreationMethod,
    onAccept: () -> Unit,
    onBack: () -> Unit,
) {
    // Copy differs per method so the user sees the security trade-offs
    // of the path they actually chose. Keeping the string list resolution
    // inside the composable (not a remember) so locale changes through
    // the in-app language picker re-pull the translated copy on recompose.
    val tips: List<String> = when (method) {
        WalletCreationMethod.SeedPhrase -> listOf(
            stringResource(R.string.wallet_create_tips_seed_1),
            stringResource(R.string.wallet_create_tips_seed_2),
            stringResource(R.string.wallet_create_tips_seed_3),
        )
        WalletCreationMethod.Passkey -> listOf(
            stringResource(R.string.wallet_create_tips_passkey_1),
            stringResource(R.string.wallet_create_tips_passkey_2),
            stringResource(R.string.wallet_create_tips_passkey_3),
        )
    }
    val checked = remember { mutableStateListOf(false, false, false) }
    val allChecked = checked.all { it }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.wallet_create_tips_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(OkaiwaColors.Lime.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = OkaiwaColors.Lime,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.wallet_create_tips_header_title),
                color = OkaiwaColors.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.wallet_create_tips_header_subtitle),
                color = OkaiwaColors.Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            tips.forEachIndexed { index, text ->
                TipRow(
                    text = text,
                    checked = checked[index],
                    onToggle = { checked[index] = !checked[index] },
                )
            }
        }

        PrimaryButton(
            label = stringResource(R.string.common_continue),
            enabled = allChecked,
            onClick = onAccept,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun TipRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) OkaiwaColors.Lime.copy(alpha = 0.08f) else OkaiwaColors.BlackElevated)
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) OkaiwaColors.Lime else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (checked) OkaiwaColors.Lime else OkaiwaColors.Muted,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = OkaiwaColors.Black,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = OkaiwaColors.White,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}

// ─────────────────────────────────────────────────────────────────────
// Step 3 — Seed phrase display
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun SeedPhraseDisplayStep(
    mnemonic: List<String>,
    savedToPasswordManager: Boolean,
    onSaved: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.wallet_create_seed_display_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            WarningBanner(
                text = stringResource(R.string.wallet_create_seed_display_warning),
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((24 / 2) * 44).dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val numbered = mnemonic.mapIndexed { i, w -> IndexedWord(i + 1, w) }
                items(items = numbered, key = { it.index }) { item ->
                    SeedWordChip(index = item.index, word = item.word)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Omnichannel save — Credential Manager picks the user's
            // preferred provider (1Password, Dashlane, Google Password
            // Manager, or the system keystore).
            val saveLabel = stringResource(
                if (savedToPasswordManager) R.string.wallet_create_seed_display_saved
                else R.string.wallet_create_seed_display_save_manager,
            )
            val noManagerMessage = stringResource(R.string.wallet_create_seed_display_no_manager_toast)
            SecondaryAction(
                icon = Icons.Default.Key,
                label = saveLabel,
                onClick = {
                    scope.launch {
                        runCatching {
                            val credMgr = CredentialManager.create(context)
                            val activity = context as? androidx.activity.ComponentActivity ?: return@launch
                            credMgr.createCredential(
                                context = activity,
                                request = CreatePasswordRequest(
                                    id = "okaiwa-wallet-seed",
                                    password = mnemonic.joinToString(" "),
                                ),
                            )
                            onSaved()
                        }.onFailure {
                            Toast.makeText(
                                context,
                                noManagerMessage,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            val copiedMessage = stringResource(R.string.wallet_create_seed_display_copied_toast)
            SecondaryAction(
                icon = Icons.Outlined.ContentCopy,
                label = stringResource(R.string.wallet_create_seed_display_copy),
                onClick = {
                    clipboard.setText(AnnotatedString(mnemonic.joinToString(" ")))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                },
            )
        }

        PrimaryButton(
            label = stringResource(R.string.wallet_create_seed_display_continue),
            enabled = true,
            onClick = onContinue,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun SeedWordChip(index: Int, word: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OkaiwaColors.BlackElevated)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            color = OkaiwaColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = word,
            color = OkaiwaColors.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun WarningBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFA726).copy(alpha = 0.12f))
            .border(1.dp, Color(0xFFFFA726).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFFFA726),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = OkaiwaColors.White,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Step 4 — Seed phrase verify
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun SeedPhraseVerifyStep(
    mnemonic: List<String>,
    verifyIndices: List<Int>,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    // For each challenged index, pick 3 decoys from the remaining words.
    val challenges = remember(mnemonic, verifyIndices) {
        verifyIndices.map { correctIdx ->
            val correct = mnemonic[correctIdx]
            val decoys = mnemonic
                .filterIndexed { i, _ -> i != correctIdx }
                .shuffled()
                .take(3)
            VerifyChallenge(
                position = correctIdx + 1,
                correctWord = correct,
                options = (decoys + correct).shuffled(),
            )
        }
    }
    val answers = remember { mutableStateListOf<String?>(null, null, null) }
    val allCorrect = challenges.indices.all { answers[it] == challenges[it].correctWord }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.wallet_create_seed_verify_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wallet_create_seed_verify_subtitle),
                color = OkaiwaColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))

            challenges.forEachIndexed { challengeIdx, challenge ->
                Text(
                    text = stringResource(R.string.wallet_create_seed_verify_word_header, challenge.position),
                    color = OkaiwaColors.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                challenge.options.forEach { candidate ->
                    val selected = answers[challengeIdx] == candidate
                    val isCorrectSelection = selected && candidate == challenge.correctWord
                    val isWrongSelection = selected && candidate != challenge.correctWord

                    val border = when {
                        isCorrectSelection -> OkaiwaColors.Lime
                        isWrongSelection -> OkaiwaColors.Error
                        else -> OkaiwaColors.BlackBorder
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OkaiwaColors.BlackElevated)
                            .border(1.5.dp, border, RoundedCornerShape(10.dp))
                            .clickable { answers[challengeIdx] = candidate }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = candidate,
                            color = OkaiwaColors.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCorrectSelection) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = OkaiwaColors.Lime,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        PrimaryButton(
            label = stringResource(R.string.wallet_create_seed_verify_continue),
            enabled = allCorrect,
            onClick = onSuccess,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

private data class VerifyChallenge(
    val position: Int,
    val correctWord: String,
    val options: List<String>,
)

private data class IndexedWord(val index: Int, val word: String)

// ─────────────────────────────────────────────────────────────────────
// Step 4b — Passkey creation (alternative to steps 3 + 4 on the
//           passkey branch; skips seed display and verification).
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun PasskeyCreationStep(onCreated: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isCreating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.wallet_create_passkey_step_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // StrongBox medallion — the visual stand-in for the
            // platform biometric + hardware-keyed signing module.
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(OkaiwaColors.Lime.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = OkaiwaColors.Lime,
                    modifier = Modifier.size(60.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.wallet_create_passkey_heading),
                color = OkaiwaColors.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wallet_create_passkey_body),
                color = OkaiwaColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Benefit list — complement to the security tips already
            // accepted on the previous step.
            PasskeyBenefitRow(
                title = stringResource(R.string.wallet_create_passkey_benefit_hardware_title),
                subtitle = stringResource(R.string.wallet_create_passkey_benefit_hardware_body),
            )
            PasskeyBenefitRow(
                title = stringResource(R.string.wallet_create_passkey_benefit_backup_title),
                subtitle = stringResource(R.string.wallet_create_passkey_benefit_backup_body),
            )
            PasskeyBenefitRow(
                title = stringResource(R.string.wallet_create_passkey_benefit_nophrase_title),
                subtitle = stringResource(R.string.wallet_create_passkey_benefit_nophrase_body),
            )
        }

        PrimaryButton(
            label = stringResource(
                if (isCreating) R.string.wallet_create_passkey_cta_creating
                else R.string.wallet_create_passkey_cta_idle,
            ),
            enabled = !isCreating,
            onClick = {
                isCreating = true
                scope.launch {
                    // Real impl: CredentialManager.createCredential(
                    //   CreatePublicKeyCredentialRequest(webAuthnOptionsJson))
                    // which triggers the platform biometric prompt and
                    // registers a passkey with the backing cloud provider.
                    // Mock: fake delay then continue.
                    kotlinx.coroutines.delay(800)
                    onCreated()
                }
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun PasskeyBenefitRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = OkaiwaColors.Lime,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OkaiwaColors.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = OkaiwaColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Step 5 — Wallet name
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun NameWalletStep(
    name: String,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val isValid = name.length in 4..24

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = stringResource(R.string.wallet_create_name_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wallet_create_name_field_label),
                color = OkaiwaColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 24) onNameChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.wallet_create_name_placeholder), color = OkaiwaColors.Placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = TextStyle(color = OkaiwaColors.White, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OkaiwaColors.Lime,
                    unfocusedBorderColor = OkaiwaColors.BlackBorder,
                    cursorColor = OkaiwaColors.Lime,
                    focusedContainerColor = OkaiwaColors.BlackElevated,
                    unfocusedContainerColor = OkaiwaColors.BlackElevated,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wallet_create_name_helper),
                color = OkaiwaColors.Muted,
                fontSize = 12.sp,
            )
        }

        PrimaryButton(
            label = stringResource(R.string.wallet_create_name_submit),
            enabled = isValid,
            onClick = onConfirm,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Step 6 — Ready
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun ReadyStep(
    walletName: String,
    onDismiss: () -> Unit,
    onFundWallet: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OkaiwaColors.BlackElevated)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.wallet_create_ready_dismiss),
                    color = OkaiwaColors.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(OkaiwaColors.Lime.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = OkaiwaColors.Lime,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.wallet_create_ready_title, walletName),
                color = OkaiwaColors.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wallet_create_ready_subtitle),
                color = OkaiwaColors.Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            PrimaryButton(
                label = stringResource(R.string.wallet_create_ready_fund_cta),
                enabled = true,
                onClick = onFundWallet,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wallet_create_ready_fund_subtitle),
                color = OkaiwaColors.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Shared controls
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
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
            text = title,
            color = OkaiwaColors.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(end = 40.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(24.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = OkaiwaColors.Lime,
            contentColor = OkaiwaColors.Black,
            disabledContainerColor = OkaiwaColors.Lime.copy(alpha = 0.3f),
            disabledContentColor = OkaiwaColors.Black.copy(alpha = 0.5f),
        ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun SecondaryAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, OkaiwaColors.BlackBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = OkaiwaColors.Lime, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = OkaiwaColors.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
