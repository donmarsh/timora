package org.marshsoft.timora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.marshsoft.timora.data.ThemeMode
import org.marshsoft.timora.ui.common.appContainer
import org.marshsoft.timora.ui.theme.Spacing

@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val versionName = remember(context) { appVersionName(context) }
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container.settingsRepository, versionName))
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onThemeMode = vm::setThemeMode,
        onDynamicColor = vm::setDynamicColor,
        onSound = vm::setSoundEnabled,
        onVibration = vm::setVibrationEnabled,
    )
}

@Suppress("DEPRECATION")
private fun appVersionName(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: Exception) {
    ""
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionCard(title = "Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(Spacing.sm))
                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.settings.themeMode == mode,
                            onClick = { onThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            modifier = Modifier.testTag("settings_theme_${mode.name}"),
                        ) { Text(mode.label()) }
                    }
                }
                if (state.dynamicColorAvailable) {
                    SwitchRow(
                        title = "Dynamic color",
                        subtitle = "Use colors from your wallpaper",
                        checked = state.settings.dynamicColor,
                        onChange = onDynamicColor,
                        tag = "settings_dynamic_color",
                    )
                }
            }
            SectionCard(title = "Cues") {
                SwitchRow(
                    title = "Sound",
                    subtitle = "Beeps on countdown and transitions",
                    checked = state.settings.soundEnabled,
                    onChange = onSound,
                    tag = "settings_sound",
                )
                SwitchRow(
                    title = "Vibration",
                    subtitle = "Haptic feedback on transitions",
                    checked = state.settings.vibrationEnabled,
                    onChange = onVibration,
                    tag = "settings_vibration",
                )
            }
            SectionCard(title = "About") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_about"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text("Timora", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Interval timer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Version ${state.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("settings_version"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
