package com.datools.qrchecker.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

private const val TAG = "QRChecker"

/**
 * Что именно случилось. Отдача выбирается по смыслу события, а не по длительности:
 * система знает про свою моторику больше, чем «повибрируй 60 миллисекунд».
 *
 * Константы платформенные: CONFIRM и REJECT - это готовые узоры «получилось» и «нет», их
 * ни с чем не спутаешь на ощупь и они одинаковы во всех приложениях телефона. На старых
 * версиях androidx подставляет ближайший доступный узор сам.
 */
enum class Outcome(internal val haptic: Int, internal val tone: Int) {
    /** Код найден и отмечен. */
    SUCCESS(HapticFeedbackConstantsCompat.CONFIRM, ToneGenerator.TONE_PROP_BEEP),

    /** Код уже был отмечен: ничего плохого, но и делать нечего. */
    REPEAT(HapticFeedbackConstantsCompat.GESTURE_END, ToneGenerator.TONE_PROP_ACK),

    /** Кода нет в этой сессии - чужая коробка. */
    FAILURE(HapticFeedbackConstantsCompat.REJECT, ToneGenerator.TONE_PROP_NACK),

    /** Свайп дотянут до порога: можно отпускать. */
    THRESHOLD(HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE, TONE_SILENT),

    /** Мелкое действие получилось: скопировали, переключили. */
    ACTION(HapticFeedbackConstantsCompat.CONTEXT_CLICK, TONE_SILENT)
}

private const val TONE_SILENT = -1

/** Отдача на действие: вибрация и, если включён, звук. */
class Feedback internal constructor(
    private val view: View,
    private val haptics: Boolean,
    private val sound: Boolean,
    private val tones: ToneGenerator?
) {
    operator fun invoke(outcome: Outcome) {
        if (haptics) {
            try {
                ViewCompat.performHapticFeedback(view, outcome.haptic)
            } catch (t: Throwable) {
                Log.w(TAG, "Haptic feedback failed", t)
            }
        }
        if (sound && outcome.tone != TONE_SILENT) {
            try {
                tones?.startTone(outcome.tone)
            } catch (t: Throwable) {
                Log.w(TAG, "Tone failed", t)
            }
        }
    }
}

/**
 * Готовит отдачу для экрана.
 *
 * Настройки читаются один раз на вход в экран: менять их на лету, пока человек сканирует,
 * незачем, а перечитывать файл на каждый код - тем более.
 */
@Composable
fun rememberFeedback(withSound: Boolean = false): Feedback {
    val view = LocalView.current
    val context = LocalContext.current
    val haptics = remember { AppSettings.haptics(context) }
    val sound = remember { withSound && AppSettings.sound(context) }

    // генератор держится, пока экран жив: создавать его на каждый код - десятки
    // миллисекунд и заметная задержка перед первым звуком
    val tones = remember(sound) { if (sound) openToneGenerator() else null }
    DisposableEffect(tones) {
        onDispose { runCatching { tones?.release() } }
    }

    return remember(view, haptics, sound, tones) { Feedback(view, haptics, sound, tones) }
}

private fun openToneGenerator(): ToneGenerator? = try {
    ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
} catch (t: Throwable) {
    // на части устройств пул тонов занят целиком: тогда экран работает молча,
    // а не падает
    Log.w(TAG, "Can't open the tone generator", t)
    null
}

private const val TONE_VOLUME = 90
