package com.datools.qrchecker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.datools.qrchecker.R
import com.datools.qrchecker.model.SessionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes the session to a file and returns an intent to send it anywhere: a messenger,
 * mail, cloud storage. The file goes to the same export directory as reports and is
 * shared through FileProvider, so the cache directory is never exposed as a whole.
 */
suspend fun shareSessionFile(context: Context, session: SessionData): Intent {
    // a batch of thousands of codes is a noticeable write
    val file = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
        File(exportDir, "${sessionFileName(session.name)}.$SESSION_FILE_EXTENSION")
            .apply { writeText(writeSessionFile(session)) }
    }

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

/** Reads the whole document; a session file is tens of kilobytes. */
fun readTextFromUri(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        ?: throw SessionFileException(R.string.session_file_unreadable)

internal fun sessionFileName(name: String): String =
    name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_').ifEmpty { "session" }
