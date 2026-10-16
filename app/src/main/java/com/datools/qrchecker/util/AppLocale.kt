package com.datools.qrchecker.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * The device language as it was when the process started, captured before the first
 * override. applyLanguage calls Locale.setDefault, after which Locale.getDefault()
 * returns the chosen language, and "System" would keep the language just switched off.
 */
private val deviceLocale: Locale = Locale.getDefault()

/**
 * Applies the language chosen in settings.
 *
 * Done by hand rather than with AppCompatDelegate.setApplicationLocales: before
 * Android 13 that only affects AppCompatActivity, and this app is a plain
 * ComponentActivity with Compose. One path for all versions.
 *
 * Called from attachBaseContext of both the application and the activity: strings come
 * from Compose and from the application context, and if the two differed the screens
 * would mix languages.
 */
fun applyLanguage(base: Context): Context {
    val locale = chosenLocale(base) ?: return base
    Locale.setDefault(locale)

    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}

/**
 * Updates the application's resources after a language change; they outlive the screen,
 * and strings resolved through them would stay in the old language until a restart.
 */
fun refreshAppLanguage(context: Context) {
    val app = context.applicationContext
    val locale = chosenLocale(app) ?: deviceLocale
    Locale.setDefault(locale)

    val resources = app.resources
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)
}

/**
 * The same context with resources in the chosen language. Everything else (starting
 * activities, file access, theme) stays with the original context: a context from
 * createConfigurationContext is not an Activity, and share sheets would not open from it.
 */
private class LocalizedContext(
    base: Context,
    private val localized: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}

/**
 * Wraps the activity context so strings come in the chosen language without recreating
 * the activity, which shows as a flash. Compose rereads strings since the language
 * choice is state.
 */
fun localizedContext(base: Context, choice: LanguageChoice): Context {
    val tag = choice.tag
    if (tag.isEmpty()) return base
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return LocalizedContext(base, base.createConfigurationContext(config).resources)
}

private fun chosenLocale(context: Context): Locale? =
    AppSettings.language(context).tag.takeIf { it.isNotEmpty() }?.let { Locale.forLanguageTag(it) }
