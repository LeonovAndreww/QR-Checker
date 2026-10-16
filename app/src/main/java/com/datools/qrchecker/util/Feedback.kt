package com.datools.qrchecker.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val TAG = "QRChecker"
private const val TONE_SILENT = -1
private const val TONE_VOLUME = 90

/**
 * What happened, with its own vibration pattern and tone.
 *
 * Patterns are set here rather than taken from the system: performHapticFeedback obeys
 * the phone's "touch vibration" setting and stays silent with it off, even though the
 * app's own switch is on, and it returns true anyway, so there is no fallback to build
 * on its result. The app has the VIBRATE permission and its own switch.
 *
 * Durations and strengths differ enough to tell events apart blind: a short firm pulse
 * for a marked code, a light double for a repeat, a heavy double for a foreign code.
 */
enum class Outcome(
    internal val timings: LongArray,
    internal val amplitudes: IntArray,
    internal val tone: Int
) {
    /** The code was found and marked. */
    SUCCESS(longArrayOf(0, 40), intArrayOf(0, 180), ToneGenerator.TONE_PROP_BEEP),

    /** The code was already marked: nothing wrong, nothing to do. */
    REPEAT(longArrayOf(0, 18, 70, 18), intArrayOf(0, 120, 0, 120), ToneGenerator.TONE_PROP_ACK),

    /** The code is not in this session: a foreign box. */
    FAILURE(longArrayOf(0, 70, 60, 70), intArrayOf(0, 255, 0, 255), ToneGenerator.TONE_PROP_NACK),

    /** A swipe reached its threshold: it can be released. */
    THRESHOLD(longArrayOf(0, 22), intArrayOf(0, 170), TONE_SILENT),

    /** A small action worked: copied, toggled. */
    ACTION(longArrayOf(0, 20), intArrayOf(0, 140), TONE_SILENT)
}

/**
 * A tone generator opened on the first sound. Lazily, since sound is usually off and
 * opening takes tens of milliseconds; and not from Compose state, because the settings
 * switch plays its sound in the click handler, before recomposition.
 */
internal class Tones : AutoCloseable {
    private var generator: ToneGenerator? = null
    private var failed = false

    fun startTone(tone: Int) {
        if (failed) return
        val open = generator ?: openToneGenerator().also {
            generator = it
            if (it == null) failed = true
        } ?: return
        open.startTone(tone)
    }

    override fun close() {
        runCatching { generator?.release() }
        generator = null
    }
}

/** Feedback for an action: vibration and, if enabled, sound. */
class Feedback internal constructor(
    private val vibrator: Vibrator?,
    private val hapticsOn: () -> Boolean,
    private val soundOn: () -> Boolean,
    private val tones: Tones
) {
    /** Vibration and sound, whichever is enabled. */
    operator fun invoke(outcome: Outcome) {
        if (hapticsOn()) vibrate(outcome)
        if (soundOn()) play(outcome)
    }

    /**
     * Vibration only, for the settings switch: turning vibration on must not beep just
     * because sound was on already.
     */
    fun vibrateOnly(outcome: Outcome) = vibrate(outcome)

    /** Sound only, played for its own switch even while sound is still off. */
    fun playOnly(outcome: Outcome) = play(outcome)

    private fun vibrate(outcome: Outcome) {
        try {
            val device = vibrator?.takeIf { it.hasVibrator() } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // with amplitudes where the motor supports them; elsewhere the system
                // reduces the pattern to on/off with the same timings
                val effect = if (device.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(outcome.timings, outcome.amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(outcome.timings, -1)
                }
                device.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(outcome.timings, -1)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Vibrate failed", t)
        }
    }

    private fun play(outcome: Outcome) {
        if (outcome.tone == TONE_SILENT) return
        try {
            tones.startTone(outcome.tone)
        } catch (t: Throwable) {
            Log.w(TAG, "Tone failed", t)
        }
    }
}

/**
 * Feedback for a screen. Settings are read at the moment of the event, so a switch
 * takes effect immediately, on the settings screen too.
 */
@Composable
fun rememberFeedback(withSound: Boolean = false): Feedback {
    val context = LocalContext.current
    val vibrator = remember(context) {
        ContextCompat.getSystemService(context.applicationContext, Vibrator::class.java)
    }
    val tones = remember { Tones() }
    DisposableEffect(tones) {
        onDispose { tones.close() }
    }

    return remember(vibrator, tones) {
        Feedback(
            vibrator = vibrator,
            hapticsOn = { AppSettings.hapticsState(context).value },
            soundOn = { withSound && AppSettings.soundState(context).value },
            tones = tones
        )
    }
}

private fun openToneGenerator(): ToneGenerator? = try {
    ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
} catch (t: Throwable) {
    // on some devices the tone pool is exhausted: then the screen stays silent, not crashed
    Log.w(TAG, "Can't open the tone generator", t)
    null
}
