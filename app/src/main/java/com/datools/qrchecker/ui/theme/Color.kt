package com.datools.qrchecker.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/*
 * Home screen accents: yellow for a new session, grey for edit, red for delete. Each has
 * its own container and content pair for the light and the dark theme, so the grey
 * button does not merge with a dark background and text on red keeps a 4.5:1 contrast.
 * In the dark theme yellow is toned down so it does not glare, and grey is lightened.
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
 * Scan feedback colours. They do not fit Material roles: "already scanned" is neither
 * an error nor a success but a warning, which the scheme lacks. The dark theme has its
 * own tones, since the light ones do not read on a dark background.
 */
internal val SuccessLight = Color(0xFF1B6B2A)
internal val SuccessDark = Color(0xFF6EDD83)
/*
 * "Already scanned" must stay yellow: a bright amber with dark text keeps the colour and
 * gives 10:1 contrast, where darkening it for white text turned it brown.
 */
internal val WarningLight = Color(0xFFFFC107)
internal val WarningDark = Color(0xFFFFCA45)
internal val OnWarning = Color(0xFF241C00)
internal val DangerLight = Color(0xFFB3261E)
internal val DangerDark = Color(0xFFFFB4AB)

/*
 * The outline drawn around a code on the camera image. Unlike the banner it carries no
 * text, so it does not need contrast with letters, only with a live picture of any
 * brightness: saturated signal tones, the same in both themes. The banner's dark green
 * and red read as dim outlines over the camera.
 */
val SignalGreen = Color(0xFF34C759)
val SignalAmber = Color(0xFFFFC107)
val SignalRed = Color(0xFFFF453A)
