package org.marshsoft.timora.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.marshsoft.timora.TimoraApplication
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {
    @Test fun applicationExposesContainerAndSeeds() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<TimoraApplication>()
        val c = app.container
        var list = c.timerRepository.observeAll().first()
        repeat(100) { if (list.size < 2) { delay(50); list = c.timerRepository.observeAll().first() } }
        assertEquals(2, list.size)
        assertEquals(true, c.settingsRepository.settings.first().soundEnabled)
        assertTrue(c.database.isOpen)
        c.database.close()
        c.applicationScope.cancel()
    }
}
