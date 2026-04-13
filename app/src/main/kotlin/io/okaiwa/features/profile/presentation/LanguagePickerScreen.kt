package io.okaiwa.features.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.okaiwa.R
import io.okaiwa.core.i18n.LocaleManager
import io.okaiwa.core.theme.OkaiwaColors

/**
 * Language picker — routes off the Profile tab's "Langue / Language" row.
 *
 * Lists the supported locales (Français, English) plus a "System default"
 * fallback that clears any stored override. Tapping a row immediately
 * applies the locale via [LocaleManager.setLocale] and pops back — the
 * activity recomposes against the new resources on the next frame
 * through AppCompatDelegate's configuration callback, so the screen the
 * user returns to is already localized.
 */
@Composable
fun LanguagePickerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Snapshot the persisted tag so the check mark reflects the current
    // preference. We read it again after any change (via the local state
    // mutation) so the row highlight stays in sync without forcing the
    // caller to round-trip through a ViewModel.
    var currentTag by remember { mutableStateOf(LocaleManager.currentTag(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(onBack = onBack)

        LanguageRow(
            label = stringResource(R.string.language_picker_system_default),
            isSelected = currentTag.isEmpty(),
            onClick = {
                LocaleManager.setLocale(context, LocaleManager.SYSTEM_DEFAULT_TAG)
                currentTag = ""
                onBack()
            },
        )
        HorizontalDivider(color = OkaiwaColors.BlackBorder, thickness = 0.5.dp)

        LanguageRow(
            label = stringResource(R.string.language_picker_option_french),
            isSelected = currentTag == "fr",
            onClick = {
                LocaleManager.setLocale(context, "fr")
                currentTag = "fr"
                onBack()
            },
        )
        HorizontalDivider(color = OkaiwaColors.BlackBorder, thickness = 0.5.dp)

        LanguageRow(
            label = stringResource(R.string.language_picker_option_english),
            isSelected = currentTag == "en",
            onClick = {
                LocaleManager.setLocale(context, "en")
                currentTag = "en"
                onBack()
            },
        )
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
            text = stringResource(R.string.language_picker_title),
            color = OkaiwaColors.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LanguageRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = OkaiwaColors.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = OkaiwaColors.Lime,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(20.dp))
        }
    }
}
