package io.okaiwa.features.contacts.presentation.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.R
import io.okaiwa.core.theme.OkaiwaColors
import io.okaiwa.features.contacts.domain.entities.DeviceContact
import io.okaiwa.features.contacts.presentation.viewmodels.ContactsViewModel
import io.okaiwa.features.contacts.presentation.viewmodels.PermissionState

/**
 * Contact picker — list the phone's address book split into Okaiwa
 * contacts (ready to message) and invite candidates (share a signup
 * link via the system sheet).
 *
 * Permission flow:
 *   - On first entry the screen asks the system for READ_CONTACTS.
 *   - If the user grants, we read ContactsContract and render the list.
 *   - If the user denies, we render a dedicated empty state with a
 *     second-chance CTA that re-prompts the dialog.
 */
@Composable
fun ContactPickerScreen(
    onBack: () -> Unit,
    onStartConversation: (DeviceContact) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> viewModel.onPermissionResult(granted) },
    )

    // Fire the system permission dialog on first composition.
    LaunchedEffect(Unit) {
        if (uiState.permissionState == PermissionState.Unknown) {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(onBack = onBack)

        when (uiState.permissionState) {
            PermissionState.Denied -> PermissionDeniedState(
                onRetry = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            )

            PermissionState.Unknown -> Spacer(modifier = Modifier.fillMaxSize())

            PermissionState.Granted -> {
                SearchField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChanged,
                )

                when {
                    uiState.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = OkaiwaColors.Lime) }

                    else -> {
                        // Pull the localized strings out of the LazyColumn
                        // body — the items-builder blocks aren't composable
                        // contexts, so `stringResource(...)` has to be
                        // hoisted to the enclosing composable where it is.
                        val sectionOkaiwa = stringResource(R.string.contact_picker_section_okaiwa)
                        val sectionInvite = stringResource(R.string.contact_picker_section_invite)
                        val inviteShareText = stringResource(R.string.contact_picker_invite_share_text)
                        // Each contact has a different display name, so we
                        // can't pre-resolve the chooser title once: capture
                        // the Context-bound getString into a lambda that we
                        // invoke at click time with the specific name.
                        val resources = context.resources
                        val chooserTitleFor: (String) -> String = { name ->
                            resources.getString(R.string.contact_picker_invite_chooser_title, name)
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (uiState.okaiwaContacts.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = sectionOkaiwa,
                                        count = uiState.okaiwaContacts.size,
                                    )
                                }
                                items(uiState.okaiwaContacts, key = { "ok-${it.id}" }) { contact ->
                                    OkaiwaContactRow(
                                        contact = contact,
                                        onClick = { onStartConversation(contact) },
                                    )
                                }
                            }
                            if (uiState.inviteContacts.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = sectionInvite,
                                        count = uiState.inviteContacts.size,
                                    )
                                }
                                items(uiState.inviteContacts, key = { "inv-${it.id}" }) { contact ->
                                    InviteContactRow(
                                        contact = contact,
                                        onClick = {
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, inviteShareText)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(share, chooserTitleFor(contact.displayName)),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
            text = stringResource(R.string.contact_picker_title),
            color = OkaiwaColors.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.contact_picker_search_placeholder), color = OkaiwaColors.Placeholder) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = OkaiwaColors.Muted)
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = TextStyle(color = OkaiwaColors.White, fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OkaiwaColors.Lime,
            unfocusedBorderColor = OkaiwaColors.BlackBorder,
            cursorColor = OkaiwaColors.Lime,
            focusedContainerColor = OkaiwaColors.BlackElevated,
            unfocusedContainerColor = OkaiwaColors.BlackElevated,
        ),
    )
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            color = OkaiwaColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.contact_picker_section_count, count),
            color = OkaiwaColors.Muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun OkaiwaContactRow(contact: DeviceContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(initial = contact.initial)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.displayName, color = OkaiwaColors.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(contact.okaiwaUsername ?: contact.phoneNumberE164, color = OkaiwaColors.Muted, fontSize = 12.sp)
        }
        Text(
            text = stringResource(R.string.contact_picker_message_action),
            color = OkaiwaColors.Lime,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InviteContactRow(contact: DeviceContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(initial = contact.initial, muted = true)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.displayName, color = OkaiwaColors.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(contact.phoneNumberE164, color = OkaiwaColors.Muted, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, OkaiwaColors.Lime, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.contact_picker_invite_action),
                color = OkaiwaColors.Lime,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Avatar(initial: String, muted: Boolean = false) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (muted) OkaiwaColors.BlackElevated
                else OkaiwaColors.Lime.copy(alpha = 0.15f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = if (muted) OkaiwaColors.Muted else OkaiwaColors.Lime,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
    }
}

@Composable
private fun PermissionDeniedState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(OkaiwaColors.Lime.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = OkaiwaColors.Lime,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.contact_picker_permission_denied_title),
            color = OkaiwaColors.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.contact_picker_permission_denied_body),
            color = OkaiwaColors.WhiteDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(OkaiwaColors.Lime)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.contact_picker_permission_denied_cta),
                color = OkaiwaColors.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}
