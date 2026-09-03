package com.datools.qrchecker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.datools.qrchecker.ui.theme.QRCheckerTheme
import com.datools.qrchecker.util.applyLanguage

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
            QRCheckerTheme {
                AppNav()
            }
        }
    }
}
