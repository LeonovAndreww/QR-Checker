package com.datools.qrchecker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.datools.qrchecker.ui.theme.QRCheckerTheme
import com.datools.qrchecker.util.AppSettings
import com.datools.qrchecker.util.applyLanguage
import com.datools.qrchecker.util.localizedContext
import com.datools.qrchecker.util.refreshAppLanguage

class MainActivity : ComponentActivity() {

    // язык подменяется до того, как экран прочитает первую строку: после setContent
    // менять уже поздно
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguage(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language by AppSettings.languageState(this)

            // Переключение языка на лету, без пересоздания экрана: язык - это состояние,
            // и все stringResource перечитываются сами. Заодно ресурсы приложения
            // догоняют выбор, потому что строки об ошибках читает ещё и ViewModel.
            val localized = remember(language) {
                refreshAppLanguage(this)
                localizedContext(this, language)
            }

            CompositionLocalProvider(LocalContext provides localized) {
                QRCheckerTheme {
                    AppNav()
                }
            }
        }
    }
}
