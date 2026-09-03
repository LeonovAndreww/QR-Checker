package com.datools.qrchecker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.datools.qrchecker.util.AppSettings
import com.datools.qrchecker.util.ThemeChoice

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Цвет плашки и цвет текста или значка на ней.
 *
 * По отдельности они не живут: тот же фон в тёмной теме светлеет, и белые буквы на нём
 * прочитать нельзя.
 */
@Immutable
data class OnColor(
    val container: Color,
    val content: Color
)

/**
 * Акценты, которых нет в схеме Material: жёлтая кнопка новой сессии, серая правка,
 * красное удаление, и три цвета обратной связи при сканировании.
 *
 * Для «этот код уже отсканирован» роли в Material нет вовсе - это не ошибка и не успех.
 * Набор держится в теме, а не в экранах, чтобы цвета не подставлялись руками и не
 * оказывались в тёмной теме нечитаемыми.
 */
@Immutable
data class AppAccents(
    val newSession: OnColor,
    val edit: OnColor,
    val delete: OnColor,
    val success: OnColor,
    val warning: OnColor,
    val danger: OnColor
)

private val LightAccents = AppAccents(
    newSession = OnColor(NewSessionYellowLight, OnNewSessionYellow),
    edit = OnColor(EditGrayLight, OnEditGray),
    delete = OnColor(DeleteRedLight, OnDeleteRedLight),
    success = OnColor(SuccessLight, Color.White),
    warning = OnColor(WarningLight, Color.White),
    danger = OnColor(DangerLight, Color.White)
)

private val DarkAccents = AppAccents(
    newSession = OnColor(NewSessionYellowDark, OnNewSessionYellow),
    edit = OnColor(EditGrayDark, OnEditGray),
    delete = OnColor(DeleteRedDark, OnDeleteRedDark),
    success = OnColor(SuccessDark, Color.Black),
    warning = OnColor(WarningDark, Color.Black),
    danger = OnColor(DangerDark, Color.Black)
)

private val LocalAppAccents = staticCompositionLocalOf { LightAccents }

val MaterialTheme.accents: AppAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalAppAccents.current

@Composable
fun QRCheckerTheme(
    // «Как в системе» - значение по умолчанию: телефон уже знает, ночь сейчас или день,
    // и переспрашивать об этом человека незачем
    choice: ThemeChoice = AppSettings.theme(LocalContext.current),
    dark: Boolean = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    },
    // Динамические цвета: на Android 12+ схема берётся из обоев телефона. Переключается
    // здесь одной строкой, если захочется вернуть прежнее поведение.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalAppAccents provides if (dark) DarkAccents else LightAccents
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
