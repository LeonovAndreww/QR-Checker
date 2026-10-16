package com.datools.qrchecker.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text a view model hands to the screen.
 *
 * A resource id instead of a finished string, so the text is resolved at composition
 * time and follows a language switch made while the message is on screen.
 */
sealed interface UiText {
    data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plain(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Plain -> text
}

/** For places outside composition, such as a snackbar shown from a coroutine. */
fun UiText.resolve(context: android.content.Context): String = when (this) {
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Plain -> text
}
