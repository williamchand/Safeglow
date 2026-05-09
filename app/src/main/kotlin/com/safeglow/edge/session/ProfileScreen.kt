package com.safeglow.edge.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Session profile screen (PROF-01).
 *
 * Three [ExposedDropdownMenuBox] selectors for:
 * 1. Pregnancy status ([PregnancyStatus])
 * 2. Regulatory jurisdiction ([Country])
 * 3. Skin concern ([SkinConcern])
 *
 * Values are ephemeral — not persisted (PROF-03). Changing values here updates
 * [SessionViewModel] StateFlow which downstream RAG/inference layers observe.
 *
 * Educational disclaimer: displayed at top of screen per PROJECT.md legal guardrail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: SessionViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Session Profile",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Educational only — not medical advice. Session context is cleared when the app closes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Pregnancy status selector
        EnumDropdown(
            label = "Pregnancy / Breastfeeding Status",
            selected = profile.pregnancyStatus,
            options = PregnancyStatus.entries,
            displayName = { it.displayName() },
            onSelected = { viewModel.setPregnancyStatus(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Country / jurisdiction selector
        EnumDropdown(
            label = "Regulatory Region",
            selected = profile.country,
            options = Country.entries,
            displayName = { it.displayName() },
            onSelected = { viewModel.setCountry(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Skin concern selector
        EnumDropdown(
            label = "Primary Skin Concern",
            selected = profile.skinConcern,
            options = SkinConcern.entries,
            displayName = { it.displayName() },
            onSelected = { viewModel.setSkinConcern(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    options: List<T>,
    displayName: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayName(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Display name extensions — human-readable labels for each enum value

private fun PregnancyStatus.displayName(): String = when (this) {
    PregnancyStatus.NOT_SET      -> "Not specified"
    PregnancyStatus.PREGNANT     -> "Pregnant / Breastfeeding"
    PregnancyStatus.NOT_PREGNANT -> "Not pregnant"
}

private fun Country.displayName(): String = when (this) {
    Country.NOT_SET -> "Not specified (strictest)"
    Country.EU      -> "European Union"
    Country.US      -> "United States"
    Country.CN      -> "China"
    Country.JP      -> "Japan"
}

private fun SkinConcern.displayName(): String = when (this) {
    SkinConcern.NOT_SET   -> "Not specified"
    SkinConcern.NORMAL    -> "Normal"
    SkinConcern.SENSITIVE -> "Sensitive"
    SkinConcern.DRY       -> "Dry"
    SkinConcern.OILY      -> "Oily"
}
