package com.datools.qrchecker

import android.app.Application
import android.content.Context
import android.util.Log
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.util.applyLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "QRChecker"

class QrCheckerApp : Application() {
    /**
     * Область корутин, живущая столько же, сколько процесс.
     *
     * Нужна для работы, которая не должна зависеть от экрана: запись отметки и копии
     * сессии, когда экран сканирования уже закрыт. Область композиции к этому моменту
     * отменена, и запись из неё не состоялась бы. Владелец у неё один на приложение, и
     * умирает она вместе с процессом.
     */
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Язык подменяется здесь, а не только на экране: строки читает и ViewModel по
     * applicationContext, и разъехавшись они дают ту самую мешанину языков.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Просроченная корзина выметается при запуске, а не по расписанию: заводить ради
        // этого работу в фоне - лишний повод для системы будить приложение.
        backgroundScope.launch {
            try {
                SessionRepository(this@QrCheckerApp).purgeOldBin()
            } catch (t: Throwable) {
                Log.w(TAG, "Can't sweep the bin", t)
            }
        }
    }
}
