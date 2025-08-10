package com.datools.qrchecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.datools.qrchecker.ui.theme.QRCheckerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QRCheckerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        "User",
                        modifier = Modifier.padding(innerPadding)
                    )

                }
            }
        }

    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Your sessions, $name:",
        modifier = modifier
            .fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    QRCheckerTheme {
        Greeting("Android")
    }
}