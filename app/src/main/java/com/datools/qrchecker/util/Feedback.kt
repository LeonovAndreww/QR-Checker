package com.datools.qrchecker.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

private const val TAG = "QRChecker"
private const val TONE_SILENT = -1
private const val TONE_VOLUME = 90

/**
 * Что именно случилось.
 *
 * Отклик выбирается по смыслу события, а не по длительности: система знает про моторику
 * своего телефона больше, чем «повибрируй 60 миллисекунд». CONFIRM и REJECT - готовые
 * узоры «получилось» и «нет», одинаковые во всех приложениях телефона, их не спутаешь на
 * ощупь. На старых версиях androidx подставляет ближайший доступный узор сам.
 *
 * [pattern] - запасной вариант на случай, если системный отклик не сработал: там узор
 * приходится задавать руками, и разница между событиями держится длительностью.
 */
enum class Outcome(
    internal val haptic: Int,
    internal val tone: Int,
    internal val pattern: LongArray
) {
    /** Код найден и отмечен. */
    SUCCESS(
        HapticFeedbackConstantsCompat.CONFIRM,
        ToneGenerator.TONE_PROP_BEEP,
        longArrayOf(0, 45)
    ),

    /** Код уже был отмечен: ничего плохого, но и делать нечего. */
    REPEAT(
        HapticFeedbackConstantsCompat.GESTURE_END,
        ToneGenerator.TONE_PROP_ACK,
        longArrayOf(0, 20, 70, 20)
    ),

    /** Кода нет в этой сессии - чужая коробка. */
    FAILURE(
        HapticFeedbackConstantsCompat.REJECT,
        ToneGenerator.TONE_PROP_NACK,
        longArrayOf(0, 90, 80, 90)
    ),

    /** Свайп дотянут до порога: можно отпускать. */
    THRESHOLD(
        HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE,
        TONE_SILENT,
        longArrayOf(0, 18)
    ),

    /** Мелкое действие получилось: скопировали, переключили. */
    ACTION(
        HapticFeedbackConstantsCompat.CONTEXT_CLICK,
        TONE_SILENT,
        longArrayOf(0, 25)
    )
}

/** Отклик на действие: вибрация и, если включён, звук. */
class Feedback internal constructor(
    private val view: View,
    private val vibrator: Vibrator?,
    private val haptics: () -> Boolean,
    private val sound: () -> Boolean,
    private val tones: () -> ToneGenerator?
) {
    operator fun invoke(outcome: Outcome) {
        if (haptics()) vibrate(outcome)
        if (sound() && outcome.tone != TONE_SILENT) {
            try {
                tones()?.startTone(outcome.tone)
            } catch (t: Throwable) {
                Log.w(TAG, "Tone failed", t)
            }
        }
    }

    /**
     * Системный отклик - и вибратор, если тот промолчал.
     *
     * performHapticFeedback подчиняется общей настройке «вибрация при нажатии»: у кого
     * она выключена, приложение молчало целиком, хотя свой тумблер в настройках включён.
     * Здесь спрашивают систему, а когда та отказывается - вибрируют сами.
     */
    private fun vibrate(outcome: Outcome) {
        try {
            val played = ViewCompat.performHapticFeedback(
                view,
                outcome.haptic,
                HapticFeedbackConstantsCompat.FLAG_IGNORE_VIEW_SETTING
            )
            if (played) return
        } catch (t: Throwable) {
            Log.w(TAG, "Haptic feedback failed", t)
        }

        try {
            val device = vibrator?.takeIf { it.hasVibrator() } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(VibrationEffect.createWaveform(outcome.pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(outcome.pattern, -1)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Vibrate failed", t)
        }
    }
}

/**
 * Готовит отклик для экрана.
 *
 * Настройки читаются на каждое событие, а не один раз на вход: тумблер переключают и тут
 * же ждут, что он подействует, - в том числе на самом экране настроек.
 */
@Composable
fun rememberFeedback(withSound: Boolean = false): Feedback {
    val view = LocalView.current
    val context = LocalContext.current
    val vibrator = remember(context) {
        ContextCompat.getSystemService(context.applicationContext, Vibrator::class.java)
    }
    val soundOn by AppSettings.soundState(context)
    val sound = withSound && soundOn

    // генератор держится, пока звук включён: создавать его на каждый код - десятки
    // миллисекунд и слышимая задержка перед первым сигналом
    val tones = remember(sound) { if (sound) openToneGenerator() else null }
    DisposableEffect(tones) {
        onDispose { runCatching { tones?.release() } }
    }

    // сами значения читаются в момент события, а не здесь: тумблер переключают и тут же
    // ждут, что он подействует
    return remember(view, vibrator, tones) {
        Feedback(
            view = view,
            vibrator = vibrator,
            haptics = { AppSettings.hapticsState(context).value },
            sound = { withSound && AppSettings.soundState(context).value },
            tones = { tones }
        )
    }
}

private fun openToneGenerator(): ToneGenerator? = try {
    ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
} catch (t: Throwable) {
    // на части устройств пул тонов занят целиком: тогда экран работает молча, а не падает
    Log.w(TAG, "Can't open the tone generator", t)
    null
}
