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
                walletName = uiState.walletName.ifBlank { "Mon portefeuille" },
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
        TopBar(title = "Créer un nouveau portefeuille", onBack = onCancel)

        Spacer(modifier = Modifier.height(8.dp))

        MethodCard(
            icon = Icons.Default.LockReset,
            title = "Phrase secrète",
            subtitle = "Afficher les détails",
            badge = "Recommandé",
            bodyLines = listOf(
                "24 mots BIP-39 générés sur votre appareil. La seule façon de récupérer vos fonds si vous perdez le téléphone.",
                "Sécurité : 256 bits d'entropie — la même norme que Ledger, Trezor, Signal.",
                "Peut être sauvegardée dans 1Password, Dashlane ou votre trousseau natif.",
            ),
            ctaLabel = "Créer",
            onClick = onPickSeedPhrase,
            isPrimary = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        MethodCard(
            icon = Icons.Default.Fingerprint,
            title = "Clé d'accès",
            subtitle = "Masquer les détails",
            badge = "Beta",
            bodyLines = listOf(
                "Créez ou récupérez un portefeuille avec une empreinte digitale. Cela se fait automatiquement grâce à la clé d'accès de votre appareil.",
                "Transaction : huit chaînes disponibles sur ce portefeuille sans étapes supplémentaires.",
                "Frais : pour les transactions courantes, comptez moins de 200 tokens de frais moyens.",
            ),
            ctaLabel = "Créer",
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
    // of the path they actually chose.
    val tips = remember(method) {
        when (method) {
            WalletCreationMethod.SeedPhrase -> listOf(
                "La phrase secrète (24 mots) est la SEULE manière de récupérer mon portefeuille. Si je la perds, mes fonds sont perdus à jamais.",
                "Je dois la conserver hors ligne — papier, coffre-fort, ou gestionnaire de mots de passe — et ne JAMAIS la partager avec qui que ce soit.",
                "Okaiwa n'a aucun moyen de récupérer ma phrase secrète à ma place. Aucun support, aucun backup serveur : la sécurité dépend entièrement de moi.",
            )
            WalletCreationMethod.Passkey -> listOf(
                "La clé privée est générée dans la StrongBox de mon téléphone. Elle ne quitte jamais l'appareil en clair et ne sera accessible qu'avec mon empreinte ou Face ID.",
                "La sauvegarde chiffrée est synchronisée via Google Password Manager (Android) ou iCloud Keychain (iOS). Je peux donc récupérer mon wallet sur un nouveau téléphone en m'authentifiant.",
                "Si je supprime la clé d'accès ET que je perds l'accès à mon compte Google/Apple, je perdrai mes fonds. Okaiwa n'a aucun backup de secours.",
            )
        }
    }
    val checked = remember { mutableStateListOf(false, false, false) }
    val allChecked = checked.all { it }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "Conseils de sécurité", onBack = onBack)

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
                text = "Votre phrase secrète est la clé de votre portefeuille",
                color = OkaiwaColors.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Cochez toutes les cases pour confirmer que vous comprenez l'importance de la phrase secrète.",
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
            label = "Continuer",
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
        TopBar(title = "Votre phrase secrète", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            WarningBanner(
                text = "Notez ces 24 mots dans l'ordre et gardez-les hors ligne. Personne — y compris Okaiwa — ne peut les récupérer à votre place.",
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
            SecondaryAction(
                icon = Icons.Default.Key,
                label = if (savedToPasswordManager) "Sauvegardée ✓" else "Enregistrer dans un gestionnaire",
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
                                "Aucun gestionnaire disponible — copiez la phrase à la main.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            SecondaryAction(
                icon = Icons.Outlined.ContentCopy,
                label = "Copier dans le presse-papiers",
                onClick = {
                    clipboard.setText(AnnotatedString(mnemonic.joinToString(" ")))
                    Toast.makeText(context, "Phrase copiée (effacez-la après sauvegarde)", Toast.LENGTH_SHORT).show()
                },
            )
        }

        PrimaryButton(
            label = "J'ai sauvegardé ma phrase",
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
        TopBar(title = "Vérifier la phrase", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sélectionnez les mots correspondants pour confirmer que vous avez sauvegardé votre phrase secrète.",
                color = OkaiwaColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))

            challenges.forEachIndexed { challengeIdx, challenge ->
                Text(
                    text = "Mot #${challenge.position}",
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
            label = "Continuer",
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
        TopBar(title = "Clé d'accès", onBack = onBack)

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
                text = "Créez votre clé d'accès",
                color = OkaiwaColors.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Votre téléphone va vous demander de confirmer avec votre empreinte ou Face ID. La clé privée reste dans la StrongBox — Okaiwa ne la voit jamais.",
                color = OkaiwaColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Benefit list — complement to the security tips already
            // accepted on the previous step.
            PasskeyBenefitRow("Génération matérielle", "Clé signée par la StrongBox, non exportable.")
            PasskeyBenefitRow("Sauvegarde cloud chiffrée", "Sync Google Password Manager / iCloud Keychain pour la récupération multi-appareil.")
            PasskeyBenefitRow("Pas de phrase à retenir", "Biométrie suffit — aucun mot de passe ni mnémonique à noter.")
        }

        PrimaryButton(
            label = if (isCreating) "Création…" else "Créer avec biométrie",
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
        TopBar(title = "Définir le nom du portefeuille", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nom du portefeuille",
                color = OkaiwaColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 24) onNameChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Mon portefeuille principal", color = OkaiwaColors.Placeholder) },
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
                text = "Entre 4 et 24 caractères.",
                color = OkaiwaColors.Muted,
                fontSize = 12.sp,
            )
        }

        PrimaryButton(
            label = "Terminé",
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
                    text = "Ignorer",
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
                text = "Parfait !\n$walletName est prêt.",
                color = OkaiwaColors.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ajoutez des fonds pour commencer à envoyer et recevoir.",
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
                label = "Alimentez votre portefeuille",
                enabled = true,
                onClick = onFundWallet,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dépôt depuis Binance, Coinbase, ou tout wallet externe.",
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
                contentDescription = "Retour",
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
