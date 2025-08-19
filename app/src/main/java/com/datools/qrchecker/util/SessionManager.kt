package com.datools.qrchecker.util

import android.content.Context
import com.datools.qrchecker.model.SessionData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import androidx.core.content.edit

class SessionManager {

    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .create()

    fun saveSession(context: Context, session: SessionData) {
        val prefs = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
        val json = gson.toJson(session)
        prefs.edit { putString(session.id, json) }
    }

    fun loadSession(context: Context, id: String): SessionData? {
        val prefs = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
        val json = prefs.getString(id, null) ?: return null
        return gson.fromJson(json, SessionData::class.java)
    }
}