package io.okaiwa.features.auth.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.okaiwa.R
import io.okaiwa.core.theme.OkaiwaColors
import io.okaiwa.features.auth.domain.entities.Countries
import io.okaiwa.features.auth.domain.entities.Country

/**
 * Full-screen country picker — opens from the phone entry screen and
 * returns the chosen country via [onSelect]. Search matches either the
 * country name or the dial code.
 */
@Composable
fun CountryPickerScreen(
    onBack: () -> Unit,
    onSelect: (Country) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        if (query.isBlank()) {
            Countries.all
        } else {
            val lower = query.trim().lowercase()
            Countries.all.filter { country ->
                // Match against the LOCALIZED name so an EN-locale user
                // searching "Germany" finds it (the hardcoded FR `name`
                // is "Allemagne"). Keep the FR fallback for users still
                // typing the French spelling.
                country.localizedName.lowercase().contains(lower) ||
                    country.name.lowercase().contains(lower) ||
                    country.dialCode.contains(lower) ||
                    country.isoCode.lowercase().contains(lower)
            }
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
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
                    text = stringResource(R.string.country_picker_title),
                    color = OkaiwaColors.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Search bar
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.country_picker_search_placeholder),
                        color = OkaiwaColors.Placeholder,
                        fontSize = 15.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = OkaiwaColors.Muted,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_clear),
                                tint = OkaiwaColors.Muted,
                            )
                        }
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = OkaiwaColors.White,
                    fontSize = 15.sp,
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = OkaiwaColors.BlackElevated,
                    unfocusedContainerColor = OkaiwaColors.BlackElevated,
                    disabledContainerColor = OkaiwaColors.BlackElevated,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = OkaiwaColors.Lime,
                ),
            )

            Spacer(modifier = Modifier.size(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    items = filtered,
                    key = { it.isoCode },
                ) { country ->
                    CountryRow(country = country, onClick = { onSelect(country) })
                    HorizontalDivider(color = OkaiwaColors.BlackBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun CountryRow(country: Country, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = country.flagEmoji,
            fontSize = 24.sp,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = country.localizedName,
            color = OkaiwaColors.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = country.dialCode,
            color = OkaiwaColors.Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
