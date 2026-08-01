package com.novaempire.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.novaempire.app.settings.AppSettings
import com.novaempire.app.ui.components.IndustrialButton
import com.novaempire.app.ui.theme.LocalThemeType
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.theme.ThemeType
import kotlin.math.roundToInt

/**
 * Écran de réglages.
 *
 * Chaque changement est appliqué et persisté **immédiatement** : tous ces réglages ont un effet
 * visible ou audible tout de suite, donc l'aperçu en direct vaut mieux qu'un brouillon validé par
 * un bouton. L'écran portait auparavant « CANCEL » et « APPLY SETTINGS », mais aucun des deux
 * n'écrivait quoi que ce soit — les réglages vivaient dans des `remember` locaux que personne ne
 * lisait.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    settings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "SYSTEM PREFERENCES",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSection(title = "AUDIO INTERFACE") {
            SettingsSlider(
                label = "Master Volume",
                value = settings.masterVolume,
                onValueChange = { onSettingsChange(settings.copy(masterVolume = it)) }
            )
            SettingsSlider(
                label = "SFX Volume",
                value = settings.sfxVolume,
                onValueChange = { onSettingsChange(settings.copy(sfxVolume = it)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = "VISUALS") {
            SettingsSwitch(
                label = "Holographic Effects",
                description = "Frosted panels, film grain, map sweep",
                checked = settings.holographicEffects,
                onCheckedChange = { onSettingsChange(settings.copy(holographicEffects = it)) },
                testTag = HOLOGRAPHIC_SWITCH_TAG
            )
            SettingsSwitch(
                label = "High Contrast Mode",
                description = "Solid map outlines, brighter secondary text",
                checked = settings.highContrast,
                onCheckedChange = { onSettingsChange(settings.copy(highContrast = it)) },
                testTag = HIGH_CONTRAST_SWITCH_TAG
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = "THEME") {
            ThemeSelector(
                preference = settings.theme,
                activeTheme = LocalThemeType.current,
                onSelect = { onSettingsChange(settings.copy(theme = it)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = "GAMEPLAY") {
            IndustrialButton(
                text = "RESET TUTORIAL DATA",
                onClick = { },
                color = NeonRed,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        IndustrialButton(
            text = "BACK",
            onClick = onBackClick,
            color = NeonCyan,
            isPrimary = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Le sélecteur qui manquait : sans lui, HALLOWEEN et WINTER n'étaient atteignables que par leur
 * fenêtre calendaire — une vingtaine de jours par an — et le joueur ne pouvait ni les demander hors
 * saison ni les refuser pendant.
 */
@Composable
fun ThemeSelector(
    preference: ThemeType?,
    activeTheme: ThemeType,
    onSelect: (ThemeType?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        THEME_OPTIONS.forEach { (value, label) ->
            IndustrialButton(
                text = label,
                onClick = { onSelect(value) },
                color = if (value == preference) NeonCyan else TextSecondary,
                isPrimary = value == preference,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(themeOptionTag(value))
            )
        }
        Text(
            text = themeStatusLabel(preference, activeTheme),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag(THEME_STATUS_TAG)
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = "${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan
            )
        )
    }
}

@Composable
fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonCyan,
                checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
            )
        )
    }
}
