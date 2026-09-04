package com.datools.qrchecker

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.datools.qrchecker.ui.theme.QRCheckerTheme
import com.datools.qrchecker.util.AppSettings
import com.datools.qrchecker.util.applyLanguage
import com.datools.qrchecker.util.localizedContext
import com.datools.qrchecker.util.refreshAppLanguage
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var localized: Resources? = null
    private var localizedTag: String? = null

    // язык подменяется до того, как экран прочитает первую строку: после setContent
    // менять уже поздно
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguage(base))
    }

    /**
     * Ресурсы самой Activity тоже следуют за выбранным языком.
     *
     * Диалог живёт в отдельном окне со своим владельцем композиции, и тот заново
     * подставляет LocalContext - контекст Activity, а не тот, что подставлен экраном.
     * Пока это не учитывалось, приложение переключалось на английский целиком, а
     * всплывающие окна оставались на языке системы.
     *
     * Метод зовут часто, поэтому результат держится до смены языка.
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

            // Переключение языка на лету, без пересоздания экрана: LocalContext -
            // статический CompositionLocal, так что его смена пересобирает всё дерево
            // ниже, и каждый stringResource перечитывается. Заодно ресурсы приложения
            // догоняют выбор - сообщения об ошибках читает ещё и ViewModel.
            val localizedForScreen = remember(language) {
                refreshAppLanguage(this)
                localizedContext(this, language)
            }

            CompositionLocalProvider(LocalContext provides localizedForScreen) {
                QRCheckerTheme {
                    // сплошная подложка цвета темы: между экранами под ними не должно
                    // просвечивать окно
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNav()
                    }
                }
            }
        }
    }
}
