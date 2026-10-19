package org.marshsoft.timora.ui.run

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CuePlayerTest {
    @Test fun androidCuePlayerNeverThrows() {
        val player = AndroidCuePlayer(ApplicationProvider.getApplicationContext())
        player.countdownBeep()
        player.transitionBeep()
        player.finishBeep()
        VibrationKind.entries.forEach { player.vibrate(it) }
        player.release()
        player.release()
        player.countdownBeep() // usable again after release
        player.release()
    }

    @Test fun monotonicTimeSourceAdvancesNonNegative() {
        assertTrue(TimeSource.Monotonic.nowMs() >= 0)
    }
}
