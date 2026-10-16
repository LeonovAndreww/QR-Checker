package com.datools.qrchecker

import com.datools.qrchecker.settings.AppSettings
import com.datools.qrchecker.settings.applyLanguage
import com.datools.qrchecker.settings.localizedContext
import com.datools.qrchecker.settings.refreshAppLanguage
import com.datools.qrchecker.ui.theme.QRCheckerTheme
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toDrawable
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var localized: Resources? = null
    private var localizedTag: String? = null

    // the language must be set before the first string is read
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguage(base))
    }

    /**
     * The activity's own resources follow the chosen language too: a dialog lives in its
     * own window, whose composition provides the activity as LocalContext again, so
     * dialogs stayed in the system language. Cached until the language changes, since
     * this is called often.
     */
    override fun getResources(): Resources {
        val base = super.getResources()
        val tag = runCatching { AppSettings.language(this).tag }.getOrDefault("")
        if (tag.isEmpty()) return base

        localized?.let { if (localizedTag == tag) return it }

        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val made = runCatching { baseContext.createConfigurationContext(config).resources }
            .getOrNull() ?: return base

        localized = made
        localizedTag = tag
        return made
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language by AppSettings.languageState(this)

            // Language switch without recreating the activity: LocalContext is a static
            // CompositionLocal, so changing it recomposes the tree below and every
            // stringResource is read again. Application resources follow as well.
            val localizedForScreen = remember(language) {
                refreshAppLanguage(this)
                localizedContext(this, language)
            }

            CompositionLocalProvider(LocalContext provides localizedForScreen) {
                QRCheckerTheme {
                    val background = MaterialTheme.colorScheme.background

                    // The window background follows the app theme. In resources it can
                    // only follow the system night mode, so a light theme on a dark phone
                    // brought back the flash between screens this colour exists to hide.
                    LaunchedEffect(background) {
                        window.setBackgroundDrawable(background.toArgb().toDrawable())
                    }

                    // a solid themed backdrop, so the window never shows between screens
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = background
                    ) {
                        AppNav()
                    }
                }
            }
        }
    }
}
