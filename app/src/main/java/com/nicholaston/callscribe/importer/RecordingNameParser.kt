package com.nicholaston.callscribe.importer

import com.nicholaston.callscribe.data.CallDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ParsedRecording(
    val startedAt: Long?,
    val direction: CallDirection = CallDirection.UNKNOWN,
    val phoneNumber: String? = null,
    val contactName: String? = null,
    val simSlot: Int? = null,
)

fun interface RecordingNameParser {
    fun parse(fileName: String): ParsedRecording?
}

object BcrRecordingParser : RecordingNameParser {
    private val dateRegex = Regex("""^(\d{8}_\d{6}\.\d{3}[+-]\d{4})(?:_|$)""")
    private val simRegex = Regex("""^sim(\d+)$""", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("""^\+?[\d*#]{3,}$""")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss.SSSxx")

    override fun parse(fileName: String): ParsedRecording? {
        val base = fileName.substringBeforeLast('.')
        val dateMatch = dateRegex.find(base) ?: return null
        val startedAt = runCatching {
            OffsetDateTime.parse(dateMatch.groupValues[1], dateFormatter).toInstant().toEpochMilli()
        }.getOrNull() ?: return null
        val tokens = base.substring(dateMatch.range.last + 1).trimStart('_')
            .split('_').filter(String::isNotBlank)

        var direction = CallDirection.UNKNOWN
        var simSlot: Int? = null
        var phone: String? = null
        val name = mutableListOf<String>()
        tokens.forEach { token ->
            when {
                token.equals("in", true) -> direction = CallDirection.INCOMING
                token.equals("out", true) -> direction = CallDirection.OUTGOING
                token.equals("conference", true) -> direction = CallDirection.CONFERENCE
                simRegex.matches(token) -> simSlot = simRegex.matchEntire(token)?.groupValues?.get(1)?.toInt()
                phone == null && phoneRegex.matches(token) -> phone = token
                else -> name += token.trim('[', ']')
            }
        }
        return ParsedRecording(
            startedAt = startedAt,
            direction = direction,
            phoneNumber = phone,
            contactName = name.joinToString(" ").ifBlank { null },
            simSlot = simSlot,
        )
    }

    fun mergeSidecar(fileName: String, json: String?): ParsedRecording? {
        val parsed = parse(fileName)
        if (json.isNullOrBlank()) return parsed
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return parsed
        val timestamp = root.long("timestamp_unix_ms")
            ?: root.string("timestamp")?.let(::parseIsoTimestamp)
        return (parsed ?: ParsedRecording(startedAt = timestamp)).copy(
            startedAt = timestamp ?: parsed?.startedAt,
            direction = root.string("direction")?.let(::parseDirection)
                ?: parsed?.direction
                ?: CallDirection.UNKNOWN,
            phoneNumber = root.string("phone_number")
                ?: root.string("phoneNumber")
                ?: parsed?.phoneNumber,
            contactName = root.string("contact_name")
                ?: root.string("contactName")
                ?: root.string("call_log_name")
                ?: parsed?.contactName,
            simSlot = root.long("sim_slot")?.toInt() ?: parsed?.simSlot,
        )
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { get(key)?.jsonPrimitive?.content?.takeIf(String::isNotBlank) }.getOrNull()

    private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

    private fun parseIsoTimestamp(value: String): Long? =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

    private fun parseDirection(value: String): CallDirection? = when (value.lowercase()) {
        "in", "incoming" -> CallDirection.INCOMING
        "out", "outgoing" -> CallDirection.OUTGOING
        "conference" -> CallDirection.CONFERENCE
        else -> null
    }
}

object SamsungRecordingParser : RecordingNameParser {
    private val date = Regex("""(\d{8})[_-](\d{6})""")
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    override fun parse(fileName: String): ParsedRecording? {
        val base = fileName.substringBeforeLast('.')
        val match = date.find(base) ?: return null
        val startedAt = localTimestamp(match.groupValues[1] + match.groupValues[2], formatter)
            ?: return null
        val label = base.removeRange(match.range).trim(' ', '_', '-')
            .removePrefix("Call recording").trim(' ', '_', '-')
        val phone = label.takeIf { it.matches(Regex("""\+?[\d -]{3,}""")) }
            ?.replace(" ", "")
        return ParsedRecording(
            startedAt = startedAt,
            phoneNumber = phone,
            contactName = label.takeIf { phone == null && it.isNotBlank() },
        )
    }
}

object PixelRecordingParser : RecordingNameParser {
    private val date = Regex("""(\d{4})(\d{2})(\d{2})[_-](\d{2})(\d{2})(\d{2})""")
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    override fun parse(fileName: String): ParsedRecording? {
        val base = fileName.substringBeforeLast('.')
        val match = date.find(base) ?: return null
        val startedAt = localTimestamp(match.groupValues.drop(1).joinToString(""), formatter)
            ?: return null
        val label = base.removeRange(match.range).trim(' ', '_', '-')
        return ParsedRecording(startedAt = startedAt, contactName = label.ifBlank { null })
    }
}

object RecordingParsers {
    private val parsers = listOf(BcrRecordingParser, SamsungRecordingParser, PixelRecordingParser)

    fun parse(fileName: String): ParsedRecording? =
        parsers.firstNotNullOfOrNull { it.parse(fileName) }
}

private fun localTimestamp(value: String, formatter: DateTimeFormatter): Long? =
    try {
        LocalDateTime.parse(value, formatter)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
