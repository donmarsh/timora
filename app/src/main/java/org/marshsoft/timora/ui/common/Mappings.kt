package org.marshsoft.timora.ui.common

import android.content.Context
import org.marshsoft.timora.TimoraApplication
import org.marshsoft.timora.data.AppContainer
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.ui.theme.SegmentTypeKey
import org.marshsoft.timora.data.ThemeMode as SettingsThemeMode
import org.marshsoft.timora.ui.theme.ThemeMode as UiThemeMode

fun SegmentType.toKey(): SegmentTypeKey = when (this) {
    SegmentType.WORK -> SegmentTypeKey.WORK
    SegmentType.REST -> SegmentTypeKey.REST
    SegmentType.PREP -> SegmentTypeKey.PREP
    SegmentType.COOLDOWN -> SegmentTypeKey.COOLDOWN
}

fun SettingsThemeMode.toUi(): UiThemeMode = when (this) {
    SettingsThemeMode.SYSTEM -> UiThemeMode.SYSTEM
    SettingsThemeMode.LIGHT -> UiThemeMode.LIGHT
    SettingsThemeMode.DARK -> UiThemeMode.DARK
}

val Context.appContainer: AppContainer
    get() = (applicationContext as TimoraApplication).container
