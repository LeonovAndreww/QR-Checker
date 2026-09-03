package com.datools.qrchecker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.datools.qrchecker.R
import com.datools.qrchecker.model.SessionData
import java.io.File

/**
 * Кладёт сессию в файл и возвращает намерение, которым его можно отдать куда угодно -
 * в мессенджер сменщику, в почту, в облако.
 *
 * Файл пишется в тот же каталог export, что и отчёты, и точно так же раздаётся через
 * FileProvider: каталог кэша целиком наружу не открывается.
 */
fun shareSessionFile(context: Context, session: SessionData): Intent {
    val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
    val file = File(exportDir, "${sessionFileName(session.name)}.$SESSION_FILE_EXTENSION")
    file.writeText(writeSessionFile(session))

    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    return Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = SESSION_FILE_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        null
    )
}

/** Читает выбранный документ целиком. Файл сессии - десятки килобайт, дробить нечего. */
fun readTextFromUri(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        ?: throw SessionFileException(R.string.session_file_unreadable)

internal fun sessionFileName(name: String): String =
    name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_').ifEmpty { "session" }
