package io.okaiwa.features.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.R
import io.okaiwa.core.theme.OkaiwaColors

/**
 * Post-OTP profile setup. Asks the user for a username (required) plus
 * optional displayName + bio. Pushes them to the Main scaffold once
 * either Submit succeeds or Skip is tapped — the SessionStore flag
 * `profileSetupDone` is flipped in both paths so the splash never
 * re-asks on subsequent cold starts.
 */
@Composable
fun ProfileSetupScreen(
    onDone: () -> Unit,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        // Lime-tinted avatar placeholder — wallet screen pattern. Tap
        // would let the user pick an avatar image; we'll wire it once
        // the encrypted attachment store ships.
        Icon(
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = OkaiwaColors.Lime,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(OkaiwaColors.Lime.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.profile_setup_title),
            color = OkaiwaColors.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.profile_setup_subtitle),
            color = OkaiwaColors.WhiteDim,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(24.dp))

        UsernameField(
            value = state.username,
            onValueChange = viewModel::onUsernameChanged,
            isValid = state.isUsernameValid || state.username.isEmpty(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.profile_setup_display_name_placeholder), color = OkaiwaColors.Placeholder)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = TextStyle(color = OkaiwaColors.White, fontSize = 15.sp),
            colors = brandColors(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.bio,
            onValueChange = viewModel::onBioChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.profile_setup_bio_placeholder), color = OkaiwaColors.Placeholder)
            },
            shape = RoundedCornerShape(12.dp),
            maxLines = 4,
            textStyle = TextStyle(color = OkaiwaColors.White, fontSize = 14.sp),
            colors = brandColors(),
        )

        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(error.toStringRes()),
                color = OkaiwaColors.Error,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = viewModel::submit,
            enabled = state.isSubmitEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OkaiwaColors.Lime,
                contentColor = OkaiwaColors.Black,
                disabledContainerColor = OkaiwaColors.LimeDim,
                disabledContentColor = OkaiwaColors.Black,
            ),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    color = OkaiwaColors.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(text = stringResource(R.string.profile_setup_submit), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = viewModel::skip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.profile_setup_skip),
                color = OkaiwaColors.WhiteDim,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun UsernameField(value: String, onValueChange: (String) -> Unit, isValid: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Live-strip non-allowed characters. Must be ASCII-only —
            // `isLetterOrDigit()` accepts Unicode letters/digits (é,
            // 漢, ٣ …) which would pass the live filter but FAIL the
            // server's `^[a-zA-Z0-9_]+$` regex, leaving the user
            // staring at characters that mysteriously block submit.
            val cleaned = newValue.filter { c ->
                (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_'
            }
            onValueChange(cleaned)
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.profile_setup_username_placeholder), color = OkaiwaColors.Placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = TextStyle(color = OkaiwaColors.White, fontSize = 16.sp),
        // Material3 1.4+ removed the public `copy(...)` on TextFieldColors —
        // recompute the whole struct via brandErrorColors() when invalid.
        colors = if (isValid) brandColors() else brandErrorColors(),
    )
}

/**
 * Map a [ProfileSetupViewModel.Error] to the localized string resource
 * the UI should render. Keeping the mapping in one place means a future
 * error variant only needs a single entry here plus a new key in
 * strings.xml / values-fr/strings.xml.
 */
private fun ProfileSetupViewModel.Error.toStringRes(): Int = when (this) {
    ProfileSetupViewModel.Error.SessionExpired -> R.string.profile_setup_error_session_expired
    ProfileSetupViewModel.Error.UsernameTaken -> R.string.profile_setup_error_username_taken
    ProfileSetupViewModel.Error.Generic -> R.string.profile_setup_error_generic
    ProfileSetupViewModel.Error.Offline -> R.string.profile_setup_error_offline
}

@Composable
private fun brandErrorColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OkaiwaColors.Error,
    unfocusedBorderColor = OkaiwaColors.Error,
    cursorColor = OkaiwaColors.Lime,
    focusedContainerColor = OkaiwaColors.BlackElevated,
    unfocusedContainerColor = OkaiwaColors.BlackElevated,
)

@Composable
private fun brandColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OkaiwaColors.Lime,
    unfocusedBorderColor = OkaiwaColors.BlackBorder,
    cursorColor = OkaiwaColors.Lime,
    focusedContainerColor = OkaiwaColors.BlackElevated,
    unfocusedContainerColor = OkaiwaColors.BlackElevated,
)
