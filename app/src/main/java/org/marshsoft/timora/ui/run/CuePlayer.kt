package org.marshsoft.timora.ui.run

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class VibrationKind { COUNTDOWN, TRANSITION, FINISH }

/** Audible / haptic cues for the run screen. Implementations must never throw. */
interface CuePlayer {
    fun countdownBeep()
    fun transitionBeep()
    fun finishBeep()
    fun vibrate(pattern: VibrationKind)
    fun release()
}

/** Thin ToneGenerator + Vibrator implementation. Every platform call is guarded. */
class AndroidCuePlayer(context: Context) : CuePlayer {
    private val appContext = context.applicationContext
    private var tone: ToneGenerator? = null
    private var toneFailed = false

    private fun generator(): ToneGenerator? {
        if (tone == null && !toneFailed) {
            tone = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            } catch (e: Exception) {
                toneFailed = true
                null
            }
        }
        return tone
    }

    private fun play(toneType: Int, durationMs: Int) {
        try {
            generator()?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            // ToneGenerator can throw on some devices; cues are best-effort.
        }
    }

    override fun countdownBeep() = play(ToneGenerator.TONE_PROP_BEEP, 120)

    override fun transitionBeep() = play(ToneGenerator.TONE_PROP_BEEP2, 250)

    override fun finishBeep() = play(ToneGenerator.TONE_PROP_ACK, 500)

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    override fun vibrate(pattern: VibrationKind) {
        try {
            val timings = when (pattern) {
                VibrationKind.COUNTDOWN -> longArrayOf(0, 40)
                VibrationKind.TRANSITION -> longArrayOf(0, 120)
                VibrationKind.FINISH -> longArrayOf(0, 200, 120, 200, 120, 400)
            }
            vibrator()?.vibrate(VibrationEffect.createWaveform(timings, -1))
        } catch (e: Exception) {
            // best-effort
        }
    }

    override fun release() {
        try {
            tone?.release()
        } catch (e: Exception) {
            // ignore
        }
        tone = null
    }

    private companion object {
        const val VOLUME = 80
    }
}
