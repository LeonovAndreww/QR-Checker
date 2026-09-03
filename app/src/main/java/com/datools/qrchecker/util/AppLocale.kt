package com.datools.qrchecker.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Подменяет язык интерфейса на выбранный в настройках.
 *
 * Делается своими руками, а не через AppCompatDelegate.setApplicationLocales: тот на
 * Android до 13 применяет язык только к экранам на AppCompatActivity, а здесь голая
 * ComponentActivity с Compose - язык бы просто не сменился на большей части устройств.
 * Здесь один путь на все версии.
 *
 * Зовётся из attachBaseContext и приложения, и экрана: строки берутся и из Compose, и из
 * ViewModel по applicationContext, и разъехавшись они дали бы ровно ту мешанину языков,
 * от которой всё это и заводится.
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
 * Догоняет уже созданные контексты после смены языка в настройках.
 *
 * Экран пересоздаётся сам, а вот ресурсы приложения живут дольше него: без этого строки
 * из ViewModel остались бы на прежнем языке до перезапуска.
 */
fun refreshAppLanguage(context: Context) {
    val app = context.applicationContext
    val locale = chosenLocale(app) ?: Locale.getDefault()
    Locale.setDefault(locale)

    val resources = app.resources
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)
}

private fun chosenLocale(context: Context): Locale? =
    AppSettings.language(context).tag.takeIf { it.isNotEmpty() }?.let { Locale.forLanguageTag(it) }
