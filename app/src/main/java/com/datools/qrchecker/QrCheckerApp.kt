package com.datools.qrchecker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Область корутин, живущая столько же, сколько процесс.
 *
 * Нужна ровно для одного: дописать копию сессии, когда экран сканирования уже закрыт.
 * Область композиции к этому моменту отменена, и запись из неё не состоялась бы. Это не
 * то же самое, что отдельный CoroutineScope на каждый экран - тот никому не принадлежал
 * и переживал экран без всякой на то причины; у этого владелец есть, он один на
 * приложение и умирает вместе с процессом.
 */
class QrCheckerApp : Application() {
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
