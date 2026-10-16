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
 * A background colour and the colour of text or icons on it. They go together: the same
 * background lightens in the dark theme, and white text on it would not read.
 */
@Immutable
data class OnColor(
    val container: Color,
    val content: Color
)

/**
 * Accents missing from the Material scheme: new session, edit and delete buttons, and
 * the three scan feedback colours ("already scanned" has no Material role). Kept in the
 * theme so screens do not hardcode colours that fail in the dark theme.
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
    warning = OnColor(WarningLight, OnWarning),
    danger = OnColor(DangerLight, Color.White)
)

private val DarkAccents = AppAccents(
    newSession = OnColor(NewSessionYellowDark, OnNewSessionYellow),
    edit = OnColor(EditGrayDark, OnEditGray),
    delete = OnColor(DeleteRedDark, OnDeleteRedDark),
    success = OnColor(SuccessDark, Color.Black),
    warning = OnColor(WarningDark, OnWarning),
    danger = OnColor(DangerDark, Color.Black)
)

private val LocalAppAccents = staticCompositionLocalOf { LightAccents }

val MaterialTheme.accents: AppAccents
    @Composable
    @ReadOnlyComposable
    get() = LocalAppAccents.current

@Composable
fun QRCheckerTheme(
    // the phone already knows whether it is day or night
    choice: ThemeChoice = AppSettings.theme(LocalContext.current),
    dark: Boolean = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    },
    // Android 12+ takes the scheme from the wallpaper
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
