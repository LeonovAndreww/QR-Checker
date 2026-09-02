package com.datools.qrchecker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Палитра выведена из иконки приложения: исходный цвет - зелёный #00A93A со скобок
 * сканера. Зелёный, а не красный лист с той же иконки: красным в приложении обозначается
 * «не хватает» и «удалить», и сделать его же цветом всех кнопок значило бы лишить его
 * смысла, а заодно слить с цветом ошибки.
 *
 * Роли построены схемой Fidelity - она держит насыщенность исходного цвета, тогда как
 * стандартная Tonal Spot увела бы зелёный в блёклый оливковый и от иконки не осталось бы
 * ничего. Третичная роль взята из Tonal Spot: у Fidelity она уходит в розовый и в тёмной
 * теме встаёт в двадцати градусах от цвета ошибки, то есть перестаёт от неё отличаться.
 *
 * Значения посчитаны, а не подобраны на глаз: тон текста и тон его подложки внутри каждой
 * роли разведены так, чтобы контраст держался в обеих темах.
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF006E23),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF00A93A),
    onPrimaryContainer = Color(0xFF00330B),
    secondary = Color(0xFF326A36),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB0EFAD),
    onSecondaryContainer = Color(0xFF366E39),
    tertiary = Color(0xFF5E622E),
    onTertiary = Color(0xFFFAFEBA),
    tertiaryContainer = Color(0xFFEFF3B0),
    onTertiaryContainer = Color(0xFF585C28),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF4FCEE),
    onBackground = Color(0xFF161D15),
    surface = Color(0xFFF4FCEE),
    onSurface = Color(0xFF161D15),
    surfaceVariant = Color(0xFFD8E7D3),
    onSurfaceVariant = Color(0xFF3D4A3B),
    outline = Color(0xFF6D7B6A),
    outlineVariant = Color(0xFFBCCBB7),
    inverseSurface = Color(0xFF2B3229),
    inverseOnSurface = Color(0xFFEBF3E5),
    inversePrimary = Color(0xFF58E169),
    scrim = Color(0xFF000000)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF58E169),
    onPrimary = Color(0xFF00390E),
    primaryContainer = Color(0xFF00A93A),
    onPrimaryContainer = Color(0xFF00330B),
    secondary = Color(0xFF98D595),
    onSecondary = Color(0xFF00390E),
    secondaryContainer = Color(0xFF1A5422),
    onSecondaryContainer = Color(0xFF8AC788),
    tertiary = Color(0xFFFAFEBB),
    onTertiary = Color(0xFF5E622E),
    tertiaryContainer = Color(0xFFECF0AD),
    onTertiaryContainer = Color(0xFF565A27),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E150D),
    onBackground = Color(0xFFDDE5D7),
    surface = Color(0xFF0E150D),
    onSurface = Color(0xFFDDE5D7),
    surfaceVariant = Color(0xFF3D4A3B),
    onSurfaceVariant = Color(0xFFBCCBB7),
    outline = Color(0xFF879583),
    outlineVariant = Color(0xFF3D4A3B),
    inverseSurface = Color(0xFFDDE5D7),
    inverseOnSurface = Color(0xFF2B3229),
    inversePrimary = Color(0xFF006E23),
    scrim = Color(0xFF000000)
)

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
