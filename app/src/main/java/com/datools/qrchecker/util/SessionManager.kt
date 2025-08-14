package com.datools.qrchecker.util

import android.content.Context
import com.datools.qrchecker.ui.SessionData
import com.google.gson.Gson

class SessionManager {

    fun saveSession(context: Context, session: SessionData) {
        val prefs = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
        val json = Gson().toJson(session)
        prefs.edit().putString(session.id, json).apply()
    }
    fun loadSession(context: Context, id: String): SessionData? {
        val prefs = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
        val json = prefs.getString(id, null) ?: return null
        return Gson().fromJson(json, SessionData::class.java)
    }

}