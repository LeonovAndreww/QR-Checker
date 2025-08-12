package com.datools.qrchecker

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
//import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.datools.qrchecker.ui.theme.QRCheckerTheme
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
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

@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(navController)
        }
        composable("createSession") {
            CreateSessionScreen(navController)
        }
        composable("scan") {
            //ScanScreen(navController, )
        }
    }
}

@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("createSession") },
                containerColor = Color.Yellow,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новая сессия")
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        Text(
            text = "Сессии",
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CreateSessionScreen(navController: NavController) {

    val context = LocalContext.current
    var sessionName by remember { mutableStateOf("") }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf("") }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                selectedPdfName = getFileName(uri, context)
                selectedPdfUri = uri
                Log.d("LogCat", "Uri открытого документа: $uri")
            }
        }
    )

    Scaffold(content = { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Настройка сессии",
                modifier = Modifier
                    .fillMaxWidth(),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            TextField(
                value = sessionName,
                onValueChange = { input ->
                    val filtered = input.filterNot { it == '\n' }
                    if (sessionName.isEmpty() && filtered.startsWith(" ")) {
                        //nothing
                    } else {
                        sessionName = filtered
                    }
                },
                label = { Text("Имя сессии") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(72.dp)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    documentPicker.launch(arrayOf("application/pdf"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(72.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(R.drawable.pdf_icon),
                        contentDescription = "PDF Icon",

                        )
                    Text(
                        text = if (selectedPdfName.isNotEmpty()) {
                            if (selectedPdfName.length > 25) {
                                selectedPdfName.take(22) + "..."
                            } else {
                                selectedPdfName
                            }
                        } else {
                            "Добавить PDF файл"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {

                    Log.d("Logcat ", "$sessionName , $selectedPdfUri")
                    navController.navigate("scan")
                },
                enabled = (sessionName.isNotBlank() && selectedPdfUri != null),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sessionName.isNotBlank() && selectedPdfUri != null) MaterialTheme.colorScheme.primary else Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(vertical = 14.dp)
                    .height(72.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Продолжить",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    })
}


@Composable
fun ScanScreen(navController: NavController, id: Long) {
    // TODO
}

//==========================================================================================================================
//can be placed in a file with utilities
private fun getFileName(uri: Uri, context: Context): String {
    var fileName = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Ошибка при получении имени файла", e)
    }
    return fileName
}

private fun QRParserPDF(uri: Uri, context: Context): List<String> {
    val bitmaps = mutableListOf<Bitmap>()
    val qrCodes = mutableListOf<String>()

    try {
        // 1. Копируем PDF во временный файл
        val tempFile = File(context.cacheDir, "temp.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        // 2. Рендерим страницы
        val fileDescriptor =
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)

        for (pageIndex in 0 until renderer.pageCount) {
            val page = renderer.openPage(pageIndex)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmaps.add(bitmap)
            page.close()
        }

        renderer.close()
        fileDescriptor.close()

        // 3. Декодируем QR-коды с каждой страницы
        val reader = MultiFormatReader()
        for (bmp in bitmaps) {
            val width = bmp.width
            val height = bmp.height
            val pixels = IntArray(width * height)
            bmp.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decode(binaryBitmap)
                qrCodes.add(result.text)
            } catch (e: Resources.NotFoundException) {
                // На этой странице QR не найден — пропускаем
            }
        }

    } catch (e: Exception) {
        Log.e("QRParserPDF", "Ошибка при парсинге PDF", e)
    }

    return qrCodes.distinct()
}

private fun decodeQRFromBitmap(bitmap: Bitmap): List<String> {
    val intArray = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

    return try {
        val reader = MultiFormatReader()
        val result: Result = reader.decode(binaryBitmap)
        listOf(result.text)
    } catch (e: Exception) {
        emptyList()
    }
}
//==========================================================================================================================