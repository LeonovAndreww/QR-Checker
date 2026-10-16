package com.datools.qrchecker.util

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "delete_confirmation"

/**
 * Sessions in which the user asked not to confirm deleting a code again.
 *
 * Kept per session rather than as one switch: in one batch codes are fixed by the dozen
 * and the question only gets in the way, in another a delete is rare and risky.
 */
@Singleton
class DeleteConfirmation @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSkipped(sessionId: String): Boolean = prefs.getBoolean(sessionId, false)

    fun skip(sessionId: String) {
        prefs.edit { putBoolean(sessionId, true) }
    }

    /** Called when the session is deleted, so the choice does not outlive it. */
    fun forget(sessionId: String) {
        prefs.edit { remove(sessionId) }
    }
}
