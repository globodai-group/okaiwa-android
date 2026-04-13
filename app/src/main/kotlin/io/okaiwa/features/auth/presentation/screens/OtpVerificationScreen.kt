package io.okaiwa.features.auth.presentation.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.okaiwa.BuildConfig
import io.okaiwa.R
import io.okaiwa.core.theme.OkaiwaColors
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * OTP verification screen — 6-digit code entry.
 *
 * A single full-width [BasicTextField] captures input; the six visual
 * slots are rendered on top via `decorationBox`. This layout means:
 *   - Paste from clipboard populates all six slots at once (the filter
 *     keeps only digits and clamps to six).
 *   - SMS Retriever auto-fill lands the entire code in one shot.
 *   - Long-press anywhere over the slot row shows the system Paste menu.
 *   - Keyboard pops up automatically on entry because the field is
 *     focused on first composition.
 *
 * The inner text field sits in the layout at alpha 0 (full size) so the
 * framework's selection + paste affordances still hit the correct region
 * — hiding it with zero size would kill those interactions.
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
    val keyboardController = LocalSoftwareKeyboardController.current

    // Request focus + bring up the keyboard once the screen is laid out.
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1.seconds)
            secondsRemaining -= 1
        }
    }

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

            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.otp_title),
                    color = OkaiwaColors.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.otp_subtitle, phoneNumberDisplay),
                    color = OkaiwaColors.WhiteDim,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Single field with six visual slots rendered via decorationBox.
            BasicTextField(
                value = otp,
                onValueChange = { newText ->
                    otp = newText.filter { it.isDigit() }.take(6)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = Color.Transparent,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (otp.length == 6 && !isLoading) onSubmit(otp)
                }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Inner field — invisible but full-size so selection
                        // handles and the paste menu target the right region.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .alpha(0f),
                        ) {
                            innerTextField()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(6) { index ->
                                val digit = otp.getOrNull(index)?.toString() ?: ""
                                val isFilled = digit.isNotEmpty()
                                val isCursor = index == otp.length && errorMessage == null
                                val borderColor = when {
                                    errorMessage != null -> OkaiwaColors.Error
                                    isCursor || isFilled -> OkaiwaColors.Lime
                                    else -> OkaiwaColors.BlackBorder
                                }
                                // weight(1f) + aspectRatio keeps all six slots
                                // the same size regardless of screen width —
                                // no more 5-uniform-plus-1-squeezed layout on
                                // narrow devices.
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(48f / 56f)
                                        .background(
                                            color = OkaiwaColors.BlackElevated,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = borderColor,
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
                },
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secondsRemaining > 0) {
                    Text(
                        text = stringResource(R.string.otp_resend_in_seconds, secondsRemaining),
                        color = OkaiwaColors.Muted,
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.otp_resend_now),
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

            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.otp_dev_hint),
                    color = OkaiwaColors.Lime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }
        }

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
                    text = stringResource(R.string.otp_submit),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
