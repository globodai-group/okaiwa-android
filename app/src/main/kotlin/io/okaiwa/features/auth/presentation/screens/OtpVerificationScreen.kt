package io.okaiwa.features.auth.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.okaiwa.core.theme.OkaiwaColors
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * OTP verification screen — 6-digit code entry.
 *
 * Visually, six rounded slots show each digit. Under the hood a single
 * invisible [BasicTextField] collects the keystrokes — this avoids the
 * per-box focus juggling that breaks SMS autofill on many devices.
 *
 * A 60-second resend countdown starts when the screen is shown; once
 * the countdown hits zero the "Renvoyer" link becomes tappable.
 */
@Composable
fun OtpVerificationScreen(
    phoneNumberDisplay: String,
    onBack: () -> Unit,
    onSubmit: (otp: String) -> Unit,
    onResend: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var otp by remember { mutableStateOf("") }
    var secondsRemaining by remember { mutableStateOf(60) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Resend countdown.
    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1.seconds)
            secondsRemaining -= 1
        }
    }

    // Auto-submit as soon as 6 digits are typed.
    LaunchedEffect(otp) {
        if (otp.length == 6 && !isLoading) {
            onSubmit(otp)
        }
    }

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
                        contentDescription = "Retour",
                        tint = OkaiwaColors.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Code à 6 chiffres",
                    color = OkaiwaColors.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Nous venons d'envoyer un code par SMS au\n$phoneNumberDisplay",
                    color = OkaiwaColors.WhiteDim,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            OtpSlotsRow(otp = otp, hasError = errorMessage != null)

            // Invisible text field that actually collects input.
            BasicTextField(
                value = otp,
                onValueChange = {
                    val digits = it.filter { ch -> ch.isDigit() }.take(6)
                    otp = digits
                },
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusRequester)
                    .focusable(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (otp.length == 6) onSubmit(otp)
                }),
                textStyle = TextStyle(color = Color.Transparent),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = OkaiwaColors.Error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Resend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secondsRemaining > 0) {
                    Text(
                        text = "Renvoyer le code dans ${secondsRemaining}s",
                        color = OkaiwaColors.Muted,
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        text = "Renvoyer le code",
                        color = OkaiwaColors.Lime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            secondsRemaining = 60
                            onResend()
                        },
                    )
                }
            }
        }

        // Submit button
        Button(
            onClick = { if (otp.length == 6) onSubmit(otp) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = otp.length == 6 && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = OkaiwaColors.Lime,
                contentColor = OkaiwaColors.Black,
                disabledContainerColor = OkaiwaColors.LimeDim,
                disabledContentColor = OkaiwaColors.Black,
            ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = OkaiwaColors.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = "Continuer",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun OtpSlotsRow(otp: String, hasError: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(6) { index ->
            val digit = otp.getOrNull(index)?.toString() ?: ""
            val isFilled = digit.isNotEmpty()
            val isFocused = index == otp.length && !hasError
            val borderColor = when {
                hasError -> OkaiwaColors.Error
                isFocused -> OkaiwaColors.Lime
                isFilled -> OkaiwaColors.Lime
                else -> OkaiwaColors.BlackBorder
            }
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(56.dp)
                    .border(
                        width = 1.5.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        color = OkaiwaColors.BlackElevated,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = digit,
                    color = OkaiwaColors.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
