package com.datools.qrchecker.util

import androidx.annotation.StringRes
import com.datools.qrchecker.R
import com.datools.qrchecker.model.SessionData
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/** Расширение и тип файла сессии. Своё расширение, чтобы файл не путали с отчётом. */
const val SESSION_FILE_EXTENSION = "qrcheck"
const val SESSION_FILE_MIME = "application/json"

private const val FORMAT = "qrchecker.session"
private const val VERSION = 1

private const val KEY_FORMAT = "format"
private const val KEY_VERSION = "version"
private const val KEY_ID = "id"
private const val KEY_NAME = "name"
private const val KEY_SAVED_AT = "savedAt"
private const val KEY_CODES = "codes"
private const val KEY_CODE = "code"
private const val KEY_SCANNED = "scanned"
private const val KEY_SCANNED_AT = "scannedAt"

/**
 * Файл не является сессией или повреждён.
 *
 * Несёт идентификатор строки, а не готовый текст: сообщение показывается пользователю, и
 * зашитое здесь по-русски вылезало бы русским и в английской локали.
 */
class SessionFileException(@get:StringRes val messageRes: Int) : Exception()

/** Прочитанная сессия вместе с временем, когда её сохранили. */
data class SessionFileContent(
    val session: SessionData,
    val savedAt: Long
)

/**
 * Пишет сессию целиком: имя, все коды в исходном порядке и отметки.
 *
 * Дерево собирается вручную, а не отражением по классу: имена полей здесь - формат
 * обмена между устройствами, и переименование поля в коде не должно ломать файлы,
 * сохранённые прошлой версией. Заодно это переживает обфускацию без keep-правил.
 */
fun writeSessionFile(
    session: SessionData,
    savedAt: Long = System.currentTimeMillis()
): String {
    val scanned = session.scannedCodes.toHashSet()

    val codes = JsonArray()
    for (code in session.codes) {
        codes.add(
            JsonObject().apply {
                addProperty(KEY_CODE, code)
                addProperty(KEY_SCANNED, code in scanned)
                // отсутствует, а не ноль: «время неизвестно» и «отмечен в начале эпохи» -
                // разные утверждения, и второе неверно
                session.scanTimes?.get(code)?.let { addProperty(KEY_SCANNED_AT, it) }
            }
        )
    }

    return JsonObject().apply {
        addProperty(KEY_FORMAT, FORMAT)
        addProperty(KEY_VERSION, VERSION)
        addProperty(KEY_ID, session.id)
        addProperty(KEY_NAME, session.name)
        addProperty(KEY_SAVED_AT, savedAt)
        add(KEY_CODES, codes)
    }.toString()
}

/**
 * Разбирает файл сессии.
 *
 * Всё, что не разобралось, поднимается как SessionFileException с внятным текстом: файл
 * приходит извне, и «ничего не произошло» - худший из возможных ответов. Незнакомые поля
 * игнорируются, чтобы файл от будущей версии всё ещё открывался.
 */
fun readSessionFile(text: String): SessionFileContent {
    val root = try {
        JsonParser.parseString(text)
    } catch (e: JsonSyntaxException) {
        throw SessionFileException(R.string.session_file_not_json)
    }

    if (!root.isJsonObject) throw SessionFileException(R.string.session_file_not_object)
    val obj = root.asJsonObject

    val format = obj.get(KEY_FORMAT)?.takeIf { it.isJsonPrimitive }?.asString
    if (format != FORMAT) throw SessionFileException(R.string.session_file_foreign)

    val version = obj.get(KEY_VERSION)?.takeIf { it.isJsonPrimitive }?.asInt
        ?: throw SessionFileException(R.string.session_file_no_version)
    if (version > VERSION) {
        throw SessionFileException(R.string.session_file_future)
    }

    val id = obj.get(KEY_ID)?.takeIf { it.isJsonPrimitive }?.asString
    if (id.isNullOrBlank()) throw SessionFileException(R.string.session_file_no_id)

    val name = obj.get(KEY_NAME)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    val codesJson = obj.get(KEY_CODES)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: throw SessionFileException(R.string.session_file_no_codes_field)

    val codes = ArrayList<String>(codesJson.size())
    val scanned = ArrayList<String>()
    val scanTimes = HashMap<String, Long>()
    for (element in codesJson) {
        if (!element.isJsonObject) continue
        val entry = element.asJsonObject
        val code = entry.get(KEY_CODE)?.takeIf { it.isJsonPrimitive }?.asString ?: continue
        if (code.isEmpty() || code in codes) continue
        codes += code
        if (entry.get(KEY_SCANNED)?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
            scanned += code
            entry.get(KEY_SCANNED_AT)?.takeIf { it.isJsonPrimitive }?.asLong
                ?.let { scanTimes[code] = it }
        }
    }

    if (codes.isEmpty()) throw SessionFileException(R.string.session_file_empty)

    val savedAt = obj.get(KEY_SAVED_AT)?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L

    return SessionFileContent(
        session = SessionData(
            id = id,
            name = name,
            codes = codes,
            scannedCodes = scanned,
            scanTimes = scanTimes
        ),
        savedAt = savedAt
    )
}
