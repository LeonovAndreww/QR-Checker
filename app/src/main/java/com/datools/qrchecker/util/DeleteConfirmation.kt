package com.datools.qrchecker.util

import android.content.Context
import androidx.core.content.edit

private const val PREFS = "delete_confirmation"

/**
 * Сессии, в которых человек попросил больше не переспрашивать перед удалением кода.
 *
 * Хранится по сессиям, а не одним выключателем на приложение: в одной партии коды правят
 * десятками и подтверждение только мешает, в другой удаление - редкое и опасное действие.
 * Переживает уход с экрана, иначе «запомнить» не значило бы ничего.
 */
object DeleteConfirmation {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSkipped(context: Context, sessionId: String): Boolean =
        prefs(context).getBoolean(sessionId, false)

    fun skip(context: Context, sessionId: String) {
        prefs(context).edit { putBoolean(sessionId, true) }
    }

    /** Вызывается при удалении сессии, чтобы настройка не пережила саму сессию. */
    fun forget(context: Context, sessionId: String) {
        prefs(context).edit { remove(sessionId) }
    }
}
