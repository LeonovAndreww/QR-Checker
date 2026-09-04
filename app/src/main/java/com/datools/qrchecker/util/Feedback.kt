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
 * Что именно случилось.
 *
 * Узор задаётся здесь, а не берётся у системы.
 *
 * Через performHapticFeedback это не работало: тот подчиняется общей настройке телефона
 * «вибрация при нажатии», и с ней выключенной молчал вообще весь отклик - хотя свой
 * тумблер в настройках включён. Хуже того, метод возвращает true и в этом случае, так
 * что и запасного пути по его ответу не построить. У приложения есть разрешение VIBRATE
 * и собственный выключатель; им и пользуемся.
 *
 * Длительности и силы подобраны так, чтобы события различались вслепую: короткий уверенный
 * толчок на отмеченный код, лёгкий двойной на повтор, тяжёлый двойной на чужой.
 */
enum class Outcome(
    internal val timings: LongArray,
    internal val amplitudes: IntArray,
    internal val tone: Int
) {
    /** Код найден и отмечен. */
    SUCCESS(longArrayOf(0, 40), intArrayOf(0, 180), ToneGenerator.TONE_PROP_BEEP),

    /** Код уже был отмечен: ничего плохого, но и делать нечего. */
    REPEAT(longArrayOf(0, 18, 70, 18), intArrayOf(0, 120, 0, 120), ToneGenerator.TONE_PROP_ACK),

    /** Кода нет в этой сессии - чужая коробка. */
    FAILURE(longArrayOf(0, 70, 60, 70), intArrayOf(0, 255, 0, 255), ToneGenerator.TONE_PROP_NACK),

    /** Свайп дотянут до порога: можно отпускать. */
    THRESHOLD(longArrayOf(0, 22), intArrayOf(0, 170), TONE_SILENT),

    /** Мелкое действие получилось: скопировали, переключили. */
    ACTION(longArrayOf(0, 20), intArrayOf(0, 140), TONE_SILENT)
}

/**
 * Генератор тонов, открываемый при первом звуке.
 *
 * Лениво, а не вместе с экраном: звук чаще выключен, а открытие занимает десятки
 * миллисекунд. И не по состоянию Compose - тумблер в настройках отвечает звуком сразу
 * в обработчике нажатия, до того как экран пересоберётся.
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

/** Отклик на действие: вибрация и, если включён, звук. */
class Feedback internal constructor(
    private val vibrator: Vibrator?,
    private val hapticsOn: () -> Boolean,
    private val soundOn: () -> Boolean,
    private val tones: Tones
) {
    /** Обычный отклик: и вибрация, и звук - смотря что включено. */
    operator fun invoke(outcome: Outcome) {
        if (hapticsOn()) vibrate(outcome)
        if (soundOn()) play(outcome)
    }

    /**
     * Только вибрация и только звук - для тумблеров в настройках.
     *
     * Общий отклик там не годится: включаешь вибрацию, а телефон пищит, потому что звук
     * был включён раньше. Тумблер должен показывать себя, а не соседа.
     */
    fun vibrateOnly(outcome: Outcome) = vibrate(outcome)

    /** Звук в ответ на свой тумблер играется и тогда, когда общий звук ещё выключен. */
    fun playOnly(outcome: Outcome) = play(outcome)

    private fun vibrate(outcome: Outcome) {
        try {
            val device = vibrator?.takeIf { it.hasVibrator() } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // с амплитудами - там, где мотор их умеет; иначе система сама сведёт
                // узор к включено/выключено по тем же длительностям
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
 * Готовит отклик для экрана.
 *
 * Значения настроек читаются в момент события, а не при входе на экран: тумблер
 * переключают и тут же ждут, что он подействует, - в том числе на самом экране настроек.
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
    // на части устройств пул тонов занят целиком: тогда экран работает молча, а не падает
    Log.w(TAG, "Can't open the tone generator", t)
    null
}
