package com.datools.qrchecker.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/** Тема оформления. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Язык интерфейса. SYSTEM - тот, что выбран в системе. */
enum class LanguageChoice(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en")
}

/** Часы: как в системе, круглосуточные или с половинами дня. */
enum class ClockChoice { SYSTEM, H24, H12 }

/**
 * Настройки внешнего вида и отклика.
 *
 * Лежат в SharedPreferences, а не в базе: их читают до того, как что-либо нарисовано, и
 * ждать ответа Room в этот момент нечего. Каждая заведена как состояние Compose, поэтому
 * экран перерисовывается сразу после переключения - без перезапуска.
 */
object AppSettings {

    private const val PREFS = "app_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_CLOCK = "clock"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_SOUND = "sound"

    // не applicationContext: язык читается из attachBaseContext приложения, когда
    // applicationContext ещё не существует
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var themeState: MutableState<ThemeChoice>? = null
    private var languageState: MutableState<LanguageChoice>? = null
    private var clockState: MutableState<ClockChoice>? = null
    private var hapticsState: MutableState<Boolean>? = null
    private var soundState: MutableState<Boolean>? = null

    fun themeState(context: Context): MutableState<ThemeChoice> =
        themeState ?: mutableStateOf(
            read(context, KEY_THEME, ThemeChoice.SYSTEM) { ThemeChoice.valueOf(it) }
        ).also { themeState = it }

    fun theme(context: Context): ThemeChoice = themeState(context).value

    fun setTheme(context: Context, choice: ThemeChoice) {
        themeState(context).value = choice
        prefs(context).edit { putString(KEY_THEME, choice.name) }
    }

    fun languageState(context: Context): MutableState<LanguageChoice> =
        languageState ?: mutableStateOf(
            read(context, KEY_LANGUAGE, LanguageChoice.SYSTEM) { LanguageChoice.valueOf(it) }
        ).also { languageState = it }

    fun language(context: Context): LanguageChoice = languageState(context).value

    fun setLanguage(context: Context, choice: LanguageChoice) {
        languageState(context).value = choice
        prefs(context).edit { putString(KEY_LANGUAGE, choice.name) }
    }

    fun clockState(context: Context): MutableState<ClockChoice> =
        clockState ?: mutableStateOf(
            read(context, KEY_CLOCK, ClockChoice.SYSTEM) { ClockChoice.valueOf(it) }
        ).also { clockState = it }

    fun clock(context: Context): ClockChoice = clockState(context).value

    fun setClock(context: Context, choice: ClockChoice) {
        clockState(context).value = choice
        prefs(context).edit { putString(KEY_CLOCK, choice.name) }
    }

    /** Отклик вибрацией включён по умолчанию: без него сканирование вслепую не подтверждается. */
    fun hapticsState(context: Context): MutableState<Boolean> =
        hapticsState ?: mutableStateOf(prefs(context).getBoolean(KEY_HAPTICS, true))
            .also { hapticsState = it }

    fun haptics(context: Context): Boolean = hapticsState(context).value

    fun setHaptics(context: Context, on: Boolean) {
        hapticsState(context).value = on
        prefs(context).edit { putBoolean(KEY_HAPTICS, on) }
    }

    /** Звук выключен по умолчанию: пищать без спроса приложение не должно. */
    fun soundState(context: Context): MutableState<Boolean> =
        soundState ?: mutableStateOf(prefs(context).getBoolean(KEY_SOUND, false))
            .also { soundState = it }

    fun sound(context: Context): Boolean = soundState(context).value

    fun setSound(context: Context, on: Boolean) {
        soundState(context).value = on
        prefs(context).edit { putBoolean(KEY_SOUND, on) }
    }

    private inline fun <T> read(context: Context, key: String, fallback: T, parse: (String) -> T): T =
        runCatching { parse(prefs(context).getString(key, null) ?: "") }.getOrDefault(fallback)
}
