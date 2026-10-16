package com.datools.qrchecker.util

import androidx.annotation.StringRes
import com.datools.qrchecker.R
import com.datools.qrchecker.model.SessionData
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/** Session file extension and type; its own extension so it is not confused with a report. */
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
private const val KEY_COLLECTING = "collecting"

/**
 * The file is not a session or is damaged. Carries a string resource rather than text,
 * so the message follows the app language.
 */
class SessionFileException(@get:StringRes val messageRes: Int) : Exception()

/** A session read from a file, with the time it was saved. */
data class SessionFileContent(
    val session: SessionData,
    val savedAt: Long
)

/**
 * Writes the whole session: name, all codes in their original order, and marks.
 *
 * The JSON tree is built by hand rather than by reflection: field names are an exchange
 * format between devices and must not change with a rename in code. It also survives
 * obfuscation without keep rules.
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
                // absent, not zero: "time unknown" and "scanned at the epoch" differ
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
        // only when set, so a regular session's file carries no unrelated fields
        if (session.collecting) addProperty(KEY_COLLECTING, true)
        add(KEY_CODES, codes)
    }.toString()
}

/**
 * Parses a session file. Anything wrong raises SessionFileException with a readable
 * message, since the file comes from outside and silence is the worst answer. Unknown
 * fields are ignored, so files from a newer version still open.
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
    val seen = HashSet<String>(codesJson.size())
    val scanned = ArrayList<String>()
    val scanTimes = HashMap<String, Long>()
    for (element in codesJson) {
        if (!element.isJsonObject) continue
        val entry = element.asJsonObject
        val code = entry.get(KEY_CODE)?.takeIf { it.isJsonPrimitive }?.asString ?: continue
        if (code.isEmpty() || !seen.add(code)) continue
        codes += code
        if (entry.get(KEY_SCANNED)?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
            scanned += code
            entry.get(KEY_SCANNED_AT)?.takeIf { it.isJsonPrimitive }?.asLong
                ?.let { scanTimes[code] = it }
        }
    }

    val collecting =
        obj.get(KEY_COLLECTING)?.takeIf { it.isJsonPrimitive }?.asBoolean == true

    // an empty collecting session is a valid file: that is how they start
    if (codes.isEmpty() && !collecting) throw SessionFileException(R.string.session_file_empty)

    val savedAt = obj.get(KEY_SAVED_AT)?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L

    return SessionFileContent(
        session = SessionData(
            id = id,
            name = name,
            codes = codes,
            scannedCodes = scanned,
            scanTimes = scanTimes,
            collecting = collecting
        ),
        savedAt = savedAt
    )
}
