package com.datools.qrchecker.util

import com.datools.qrchecker.model.SessionData

/**
 * Что приложение отвечает на предъявленный код.
 *
 * Вынесено из экрана отдельно: это единственное место, где решается, что вообще
 * происходит при сканировании, и внутри composable его нельзя ни прочитать целиком, ни
 * проверить тестом.
 */
sealed interface ScanVerdict {

    val code: String

    /** Код был в списке и отмечен впервые. Обычная сессия. */
    data class Marked(override val code: String) : ScanVerdict

    /** Кода в сессии не было, и он в неё дописан. Только собирающая сессия. */
    data class Recorded(override val code: String) : ScanVerdict

    /** Код уже отмечен. [at] - когда именно, null если время неизвестно. */
    data class Repeat(override val code: String, val at: Long?) : ScanVerdict

    /** Кода нет в этой поставке. У собирающей сессии не бывает. */
    data class Foreign(override val code: String) : ScanVerdict
}

/**
 * Порядок проверок важен.
 *
 * Повтор проверяется первым: отмеченный код остаётся отмеченным в любой сессии, и
 * собирающая не должна дописывать его заново. Дальше - список: код из документа
 * отмечается. И только потом расходятся пути: собирающая дописывает незнакомый код,
 * сверяющая называет его чужим.
 */
fun verdictFor(session: SessionData, code: String): ScanVerdict = when {
    code in session.scannedCodes -> ScanVerdict.Repeat(code, session.scanTimes?.get(code))
    code in session.codes -> ScanVerdict.Marked(code)
    session.collecting -> ScanVerdict.Recorded(code)
    else -> ScanVerdict.Foreign(code)
}
