package com.datools.qrchecker.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/*
 * Акценты главного экрана: жёлтая кнопка новой сессии, серая правка, красное удаление.
 *
 * Раньше они стояли прямо в коде экрана как Color.Yellow, Color.DarkGray и Color.Red, то
 * есть одинаковыми в обеих темах, и в тёмной разваливались: тёмно-серая кнопка сливалась с
 * фоном, а белый текст на чистом красном давал контраст 3.9 при норме 4.5. Здесь у каждого
 * акцента своя пара «фон/текст» на светлую и на тёмную тему.
 *
 * Оттенки подобраны так, чтобы вид не поменялся: жёлтый остался жёлтым, красный красным.
 * В тёмной теме жёлтый чуть приглушён, иначе он бьёт по глазам на тёмном фоне, а серый
 * наоборот осветлён, иначе кнопки на фоне просто не видно.
 */
internal val NewSessionYellowLight = Color(0xFFFFFF00)
internal val NewSessionYellowDark = Color(0xFFFFD54F)
internal val OnNewSessionYellow = Color(0xFF000000)

internal val EditGrayLight = Color(0xFF3F3F3F)
internal val EditGrayDark = Color(0xFF707070)
internal val OnEditGray = Color(0xFFFFFFFF)

internal val DeleteRedLight = Color(0xFFE02020)
internal val DeleteRedDark = Color(0xFFEF5350)
internal val OnDeleteRedLight = Color(0xFFFFFFFF)
internal val OnDeleteRedDark = Color(0xFF1A0000)

/*
 * Цвета обратной связи при сканировании. В роли Material они не ложатся: «этот код уже
 * отсканирован» - не ошибка и не успех, а предупреждение, которого в схеме нет вовсе.
 * Для тёмной темы взяты отдельные тона: те же самые на тёмном фоне не читаются.
 */
internal val SuccessLight = Color(0xFF1B6B2A)
internal val SuccessDark = Color(0xFF6EDD83)
internal val WarningLight = Color(0xFF8A5000)
internal val WarningDark = Color(0xFFFFB868)
internal val DangerLight = Color(0xFFB3261E)
internal val DangerDark = Color(0xFFFFB4AB)
