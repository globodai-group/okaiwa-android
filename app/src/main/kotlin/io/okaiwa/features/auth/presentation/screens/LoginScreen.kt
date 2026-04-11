package io.okaiwa.features.auth.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.features.auth.presentation.viewmodels.AuthStep
import io.okaiwa.features.auth.presentation.viewmodels.AuthViewModel

/**
 * Login / registration screen.
 *
 * Displays a phone number input field with country code.
 * On submission, requests an OTP and transitions to verification.
 * Uses Material 3 components with Okaiwa branding.
 */
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.step) {
        if (uiState.step == AuthStep.Complete) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App icon and title
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Okaiwa",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Secure messaging.\nYour keys. Your data.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (uiState.step) {
                AuthStep.PhoneInput -> {
                    PhoneInputSection(
                        phoneNumber = uiState.phoneNumber,
                        onPhoneChanged = viewModel::onPhoneNumberChanged,
                        isLoading = uiState.isLoading,
                        isValid = uiState.isPhoneValid,
                        onSubmit = {
                            keyboardController?.hide()
                            viewModel.requestOtp()
                        },
                    )
                }

                AuthStep.OtpVerification -> {
                    OtpVerificationSection(
                        otpCode = uiState.otpCode,
                        onOtpChanged = viewModel::onOtpCodeChanged,
                        isLoading = uiState.isLoading,
                        isComplete = uiState.isOtpComplete,
                        onSubmit = {
                            keyboardController?.hide()
                            viewModel.verifyOtp()
                        },
                        onBack = viewModel::goBackToPhoneInput,
                    )
                }

                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PhoneInputSection(
    phoneNumber: String,
    onPhoneChanged: (String) -> Unit,
    isLoading: Boolean,
    isValid: Boolean,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = onPhoneChanged,
        label = { Text("Phone number") },
        placeholder = { Text("+33 6 12 34 56 78") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = isValid && !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text("Continue")
        }
    }
}

@Composable
private fun OtpVerificationSection(
    otpCode: String,
    onOtpChanged: (String) -> Unit,
    isLoading: Boolean,
    isComplete: Boolean,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "Enter the 6-digit code",
        style = MaterialTheme.typography.titleMedium,
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = otpCode,
        onValueChange = onOtpChanged,
        label = { Text("Verification code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = isComplete && !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text("Verify")
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(onClick = onBack) {
        Text("Change phone number")
    }
}
