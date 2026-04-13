package io.okaiwa.features.auth.presentation.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.okaiwa.R
import io.okaiwa.core.theme.OkaiwaColors
import io.okaiwa.features.auth.domain.entities.Countries
import io.okaiwa.features.auth.domain.entities.Country

/**
 * Phone number entry screen — Telegram-style layout with Okaiwa branding.
 *
 * Structure:
 *  - Top bar: back button only.
 *  - Title + subtitle centered.
 *  - Country row (tap to open the country picker bottom sheet).
 *  - Phone number input with the dial-code prefix baked in.
 *  - "Synchroniser les contacts" checkbox (opt-in, privacy-preserving
 *    contact discovery using PBKDF2-hashed numbers — the real hash
 *    is computed only after OTP verification).
 *  - Floating lime FAB to submit.
 */
@Composable
fun PhoneNumberScreen(
    mode: PhoneEntryMode,
    onBack: () -> Unit,
    onContinue: (country: Country, nationalNumber: String, syncContacts: Boolean) -> Unit,
    onPickCountry: () -> Unit,
    selectedCountry: Country = Countries.default,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    /**
     * Login-only surface: when the user typed a phone that has no
     * Okaiwa account, the screen shows a friendly French CTA instead
     * of a technical error. Tapping it flips the flow to Register
     * for the same number without retyping.
     */
    accountNotFoundForLogin: Boolean = false,
    onCreateAccountFromLogin: () -> Unit = {},
) {
    var phoneDigits by rememberSaveable { mutableStateOf("") }
    var syncContacts by rememberSaveable { mutableStateOf(true) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isSubmittable = phoneDigits.length >= 6 && !isLoading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = OkaiwaColors.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when (mode) {
                        PhoneEntryMode.Register -> stringResource(R.string.phone_entry_title_register)
                        PhoneEntryMode.Login -> stringResource(R.string.phone_entry_title_login)
                    },
                    color = OkaiwaColors.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.phone_entry_subtitle),
                    color = OkaiwaColors.WhiteDim,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CountryField(
                    country = selectedCountry,
                    onClick = onPickCountry,
                )

                PhoneField(
                    dialCode = selectedCountry.dialCode,
                    digits = phoneDigits,
                    onDigitsChange = { phoneDigits = it.filter { ch -> ch.isDigit() } },
                    onSubmit = {
                        if (isSubmittable) {
                            keyboardController?.hide()
                            onContinue(selectedCountry, phoneDigits, syncContacts)
                        }
                    },
                )

                SyncContactsToggle(
                    checked = syncContacts,
                    onCheckedChange = { syncContacts = it },
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = OkaiwaColors.Error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (accountNotFoundForLogin) {
                    // Friendly, French-first fallback when Login mode
                    // hits a 404. The CTA flips to Register with the
                    // same phone number, so the user keeps their
                    // momentum instead of being bounced back to the
                    // welcome screen.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = OkaiwaColors.Lime.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .background(
                                color = OkaiwaColors.Lime.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.phone_entry_login_account_not_found_title),
                            color = OkaiwaColors.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.phone_entry_login_account_not_found_body),
                            color = OkaiwaColors.WhiteDim,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onCreateAccountFromLogin,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OkaiwaColors.Lime,
                                contentColor = OkaiwaColors.Black,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.phone_entry_login_create_account_cta),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        // Submit FAB
        FloatingActionButton(
            onClick = {
                if (isSubmittable) {
                    keyboardController?.hide()
                    onContinue(selectedCountry, phoneDigits, syncContacts)
                }
            },
            containerColor = if (isSubmittable) OkaiwaColors.Lime else OkaiwaColors.LimeDim,
            contentColor = OkaiwaColors.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(64.dp),
            shape = RoundedCornerShape(32.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = OkaiwaColors.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.phone_entry_continue_cd),
                )
            }
        }
    }
}

enum class PhoneEntryMode { Register, Login }

@Composable
private fun CountryField(country: Country, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = OkaiwaColors.BlackBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = country.flagEmoji,
            fontSize = 22.sp,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.phone_entry_country_label),
                color = OkaiwaColors.Muted,
                fontSize = 11.sp,
            )
            Text(
                text = country.localizedName,
                color = OkaiwaColors.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = country.dialCode,
            color = OkaiwaColors.WhiteDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = OkaiwaColors.Muted,
        )
    }
}

@Composable
private fun PhoneField(
    dialCode: String,
    digits: String,
    onDigitsChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    TextField(
        value = digits,
        onValueChange = onDigitsChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        label = {
            Text(
                text = stringResource(R.string.phone_entry_phone_field_label),
                color = OkaiwaColors.Lime,
                fontSize = 12.sp,
            )
        },
        leadingIcon = {
            Text(
                text = dialCode,
                color = OkaiwaColors.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
        singleLine = true,
        textStyle = TextStyle(
            color = OkaiwaColors.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        ),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = OkaiwaColors.Lime,
            unfocusedIndicatorColor = OkaiwaColors.BlackBorder,
            cursorColor = OkaiwaColors.Lime,
        ),
    )
}

@Composable
private fun SyncContactsToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = OkaiwaColors.Lime,
                checkmarkColor = OkaiwaColors.Black,
                uncheckedColor = OkaiwaColors.BlackBorder,
            ),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.phone_entry_sync_contacts_label),
            color = OkaiwaColors.White,
            fontSize = 15.sp,
        )
    }
}
