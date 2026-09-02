package com.datools.qrchecker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Цвет плашки и цвет текста на ней.
 *
 * По отдельности они не живут: в тёмной теме плашка светлая, и белый текст на ней
 * прочитать нельзя.
 */
@Immutable
data class FeedbackColor(
    val container: Color,
    val content: Color
)

/**
 * Цвета обратной связи на экране сканирования: успех, предупреждение, отказ.
 *
 * В цветовой схеме Material для «этот код уже отсканирован» роли нет - это не ошибка и не
 * успех. Отдельный набор в теме нужен затем, чтобы экран не подставлял цвета руками и не
 * оказался в тёмной теме с нечитаемыми.
 */
@Immutable
data class FeedbackColors(
    val success: FeedbackColor,
    val warning: FeedbackColor,
    val danger: FeedbackColor
)

private val LightFeedback = FeedbackColors(
    success = FeedbackColor(SuccessLight, Color.White),
    warning = FeedbackColor(WarningLight, Color.White),
    danger = FeedbackColor(DangerLight, Color.White)
)

private val DarkFeedback = FeedbackColors(
    success = FeedbackColor(SuccessDark, Color.Black),
    warning = FeedbackColor(WarningDark, Color.Black),
    danger = FeedbackColor(DangerDark, Color.Black)
)

private val LocalFeedbackColors = staticCompositionLocalOf { LightFeedback }

val MaterialTheme.feedback: FeedbackColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFeedbackColors.current

@Composable
fun QRCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Динамические цвета выключены намеренно. С ними приложение перекрашивалось под обои
    // телефона, то есть выглядело по-разному у каждого и не совпадало с собственной
    // иконкой ни у кого. Для инструмента, где цветом обозначено «отсканировано» и «не
    // хватает», ещё и рискованно: обои решали бы, какого цвета успех.
    val colorScheme = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalFeedbackColors provides if (darkTheme) DarkFeedback else LightFeedback
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
