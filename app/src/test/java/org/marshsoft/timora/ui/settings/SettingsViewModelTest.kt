package org.marshsoft.timora.ui.settings

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.marshsoft.timora.data.AppSettings
import org.marshsoft.timora.data.SettingsRepository
import org.marshsoft.timora.data.ThemeMode

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeSettings : SettingsRepository {
        val flow = MutableStateFlow(AppSettings())
        override val settings = flow
        override suspend fun setThemeMode(mode: ThemeMode) { flow.value = flow.value.copy(themeMode = mode) }
        override suspend fun setDynamicColor(enabled: Boolean) { flow.value = flow.value.copy(dynamicColor = enabled) }
        override suspend fun setSoundEnabled(enabled: Boolean) { flow.value = flow.value.copy(soundEnabled = enabled) }
        override suspend fun setVibrationEnabled(enabled: Boolean) { flow.value = flow.value.copy(vibrationEnabled = enabled) }
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun exposesSettingsAndInjectedFlags() = runTest(dispatcher) {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo, dynamicColorAvailable = true, versionName = "1.2.3")
        vm.state.test {
            val initial = awaitItem()
            assertEquals("1.2.3", initial.versionName)
            assertEquals(true, initial.dynamicColorAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun dynamicColorUnavailableFlagPropagates() = runTest(dispatcher) {
        val vm = SettingsViewModel(FakeSettings(), dynamicColorAvailable = false)
        vm.state.test {
            assertFalse(awaitItem().dynamicColorAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun settersPersistThroughRepository() = runTest(dispatcher) {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo, dynamicColorAvailable = true)
        vm.setThemeMode(ThemeMode.DARK)
        vm.setDynamicColor(false)
        vm.setSoundEnabled(false)
        vm.setVibrationEnabled(false)
        advanceUntilIdle()
        assertEquals(AppSettings(ThemeMode.DARK, false, false, false), repo.settings.first())
    }

    @Test fun stateReflectsRepositoryUpdates() = runTest(dispatcher) {
        val repo = FakeSettings()
        val vm = SettingsViewModel(repo, dynamicColorAvailable = true)
        vm.state.test {
            awaitItem()
            repo.flow.value = AppSettings(themeMode = ThemeMode.LIGHT)
            var latest = awaitItem()
            while (latest.settings.themeMode != ThemeMode.LIGHT) latest = awaitItem()
            assertEquals(ThemeMode.LIGHT, latest.settings.themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
