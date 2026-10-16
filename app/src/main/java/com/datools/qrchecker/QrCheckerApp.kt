package com.datools.qrchecker

import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.di.ApplicationScope
import com.datools.qrchecker.settings.applyLanguage
import android.app.Application
import android.content.Context
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class QrCheckerApp : Application() {

    @Inject lateinit var repository: SessionRepository

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    // The app context reads strings too (view models, notifications), so it follows the
    // language chosen in the settings, not only the activity.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Swept at start-up rather than on a schedule: no reason to wake the app for it.
        scope.launch {
            try {
                repository.purgeOldBin()
            } catch (t: Throwable) {
                Log.w("QrCheckerApp", "Can't sweep the bin", t)
            }
        }
    }
}
