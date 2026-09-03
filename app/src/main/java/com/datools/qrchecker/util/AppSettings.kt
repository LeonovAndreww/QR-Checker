package com.datools.qrchecker.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** Тема оформления. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Язык интерфейса. SYSTEM - тот, что выбран в системе. */
enum class LanguageChoice(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en")
}

/**
 * Настройки внешнего вида и отдачи.
 *
 * Живут в SharedPreferences, а не в базе: их читают до того, как что-либо нарисовано, и
 * ждать ответа Room в этот момент нечего. Чтения синхронные и дешёвые - это один xml.
 */
object AppSettings {

    private const val PREFS = "app_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_SOUND = "sound"

    // не applicationContext: язык читается из attachBaseContext приложения, когда
    // applicationContext ещё не существует
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Тема живёт состоянием Compose, а не просто в файле: её меняют на том же экране,
     * который перекрашивается, и перезапускать ради этого нечего.
     */
    private var themeState: MutableState<ThemeChoice>? = null

    fun theme(context: Context): ThemeChoice = themeStateOf(context).value

    fun setTheme(context: Context, choice: ThemeChoice) {
        themeStateOf(context).value = choice
        prefs(context).edit { putString(KEY_THEME, choice.name) }
    }

    internal fun themeStateOf(context: Context): MutableState<ThemeChoice> =
        themeState ?: mutableStateOf(storedTheme(context)).also { themeState = it }

    private fun storedTheme(context: Context): ThemeChoice =
        runCatching { ThemeChoice.valueOf(prefs(context).getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeChoice.SYSTEM)

    fun language(context: Context): LanguageChoice =
        runCatching {
            LanguageChoice.valueOf(prefs(context).getString(KEY_LANGUAGE, null) ?: "")
        }.getOrDefault(LanguageChoice.SYSTEM)

    fun setLanguage(context: Context, choice: LanguageChoice) {
        prefs(context).edit { putString(KEY_LANGUAGE, choice.name) }
    }

    /** Отдача включена по умолчанию: без неё сканирование вслепую не подтверждается ничем. */
    fun haptics(context: Context): Boolean = prefs(context).getBoolean(KEY_HAPTICS, true)

    fun setHaptics(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY_HAPTICS, on) }
    }

    /** Звук выключен по умолчанию: склад складом, но пищать без спроса приложение не должно. */
    fun sound(context: Context): Boolean = prefs(context).getBoolean(KEY_SOUND, false)

    fun setSound(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY_SOUND, on) }
    }
}
