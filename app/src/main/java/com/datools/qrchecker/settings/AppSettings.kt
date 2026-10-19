package com.datools.qrchecker.settings

import com.datools.qrchecker.codes.codeFields
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Interface language; SYSTEM follows the system setting. */
enum class LanguageChoice(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en")
}

/** Clock: as in the system, 24-hour or 12-hour. */
enum class ClockChoice { SYSTEM, H24, H12 }

/** Where the scan banner is: at the top, or at the bottom, nearer the thumb. */
enum class BannerPosition { TOP, BOTTOM }

/**
 * Appearance and feedback settings.
 *
 * Kept in SharedPreferences rather than the database: they are read before anything is
 * drawn. Each is exposed as Compose state, so a switch applies without a restart.
 */
object AppSettings {

    private const val PREFS = "app_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_CLOCK = "clock"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_SOUND = "sound"
    private const val KEY_CODE_FIELDS = "code_fields"
    private const val KEY_SCAN_CODE = "scan_code"
    private const val KEY_CODE_KIND = "code_kind"
    private const val KEY_SCROLLBAR = "scrollbar"
    private const val KEY_SCAN_ON_TAP = "scan_on_tap"
    private const val KEY_BANNER_POSITION = "banner_position"

    // not applicationContext: the language is read in the application's
    // attachBaseContext, before applicationContext exists
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var themeState: MutableState<ThemeChoice>? = null
    private var languageState: MutableState<LanguageChoice>? = null
    private var clockState: MutableState<ClockChoice>? = null
    private var bannerPositionState: MutableState<BannerPosition>? = null
    private var hapticsState: MutableState<Boolean>? = null
    private var soundState: MutableState<Boolean>? = null
    private var codeFieldsState: MutableState<Boolean>? = null
    private var scanCodeState: MutableState<Boolean>? = null
    private var codeKindState: MutableState<Boolean>? = null
    private var scrollbarState: MutableState<Boolean>? = null
    private var scanOnTapState: MutableState<Boolean>? = null

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

    /** On by default: without it a blind scan gets no confirmation. */
    fun hapticsState(context: Context): MutableState<Boolean> =
        hapticsState ?: mutableStateOf(prefs(context).getBoolean(KEY_HAPTICS, true))
            .also { hapticsState = it }

    fun haptics(context: Context): Boolean = hapticsState(context).value

    fun setHaptics(context: Context, on: Boolean) {
        hapticsState(context).value = on
        prefs(context).edit { putBoolean(KEY_HAPTICS, on) }
    }

    /** Off by default: an app should not beep unasked. */
    fun soundState(context: Context): MutableState<Boolean> =
        soundState ?: mutableStateOf(prefs(context).getBoolean(KEY_SOUND, false))
            .also { soundState = it }

    fun sound(context: Context): Boolean = soundState(context).value

    fun setSound(context: Context, on: Boolean) {
        soundState(context).value = on
        prefs(context).edit { putBoolean(KEY_SOUND, on) }
    }

    /** Expiry and batch under each code. On by default: the data is in the code anyway. */
    fun codeFieldsState(context: Context): MutableState<Boolean> =
        codeFieldsState ?: mutableStateOf(prefs(context).getBoolean(KEY_CODE_FIELDS, true))
            .also { codeFieldsState = it }

    fun codeFields(context: Context): Boolean = codeFieldsState(context).value

    fun setCodeFields(context: Context, on: Boolean) {
        codeFieldsState(context).value = on
        prefs(context).edit { putBoolean(KEY_CODE_FIELDS, on) }
    }

    /**
     * The code itself in the scan banner. On by default: the printed text under a Data
     * Matrix is often missing, and without it there is nothing to check a foreign code by.
     */
    fun scanCodeState(context: Context): MutableState<Boolean> =
        scanCodeState ?: mutableStateOf(prefs(context).getBoolean(KEY_SCAN_CODE, true))
            .also { scanCodeState = it }

    fun scanCode(context: Context): Boolean = scanCodeState(context).value

    fun setScanCode(context: Context, on: Boolean) {
        scanCodeState(context).value = on
        prefs(context).edit { putBoolean(KEY_SCAN_CODE, on) }
    }

    /**
     * The code's kind (Chestny ZNAK, GS1, EAN-13...) under each code. Off by default:
     * within one batch every code has the same kind.
     */
    fun codeKindState(context: Context): MutableState<Boolean> =
        codeKindState ?: mutableStateOf(prefs(context).getBoolean(KEY_CODE_KIND, false))
            .also { codeKindState = it }

    fun setCodeKind(context: Context, on: Boolean) {
        codeKindState(context).value = on
        prefs(context).edit { putBoolean(KEY_CODE_KIND, on) }
    }

    /** Scrollbar in long lists. On by default: a batch can be thousands of boxes. */
    fun scrollbarState(context: Context): MutableState<Boolean> =
        scrollbarState ?: mutableStateOf(prefs(context).getBoolean(KEY_SCROLLBAR, true))
            .also { scrollbarState = it }

    fun setScrollbar(context: Context, on: Boolean) {
        scrollbarState(context).value = on
        prefs(context).edit { putBoolean(KEY_SCROLLBAR, on) }
    }

    /** The banner at the top by default: it is where the eye goes from the code. */
    fun bannerPositionState(context: Context): MutableState<BannerPosition> =
        bannerPositionState ?: mutableStateOf(
            read(context, KEY_BANNER_POSITION, BannerPosition.TOP) { BannerPosition.valueOf(it) }
        ).also { bannerPositionState = it }

    fun setBannerPosition(context: Context, position: BannerPosition) {
        bannerPositionState(context).value = position
        prefs(context).edit { putString(KEY_BANNER_POSITION, position.name) }
    }

    /**
     * Decode only on a tap. Off by default, since boxes are usually just held up to the
     * camera; useful with many codes side by side, and it saves battery between taps.
     */
    fun scanOnTapState(context: Context): MutableState<Boolean> =
        scanOnTapState ?: mutableStateOf(prefs(context).getBoolean(KEY_SCAN_ON_TAP, false))
            .also { scanOnTapState = it }

    fun setScanOnTap(context: Context, on: Boolean) {
        scanOnTapState(context).value = on
        prefs(context).edit { putBoolean(KEY_SCAN_ON_TAP, on) }
    }

    private inline fun <T> read(context: Context, key: String, fallback: T, parse: (String) -> T): T =
        runCatching { parse(prefs(context).getString(key, null) ?: "") }.getOrDefault(fallback)
}
