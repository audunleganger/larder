package com.caloriecompanion.shared.domain

import com.caloriecompanion.shared.api.ErrorCodes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A failure the API reports to the client with [code] and HTTP [status]. */
class AppException(
    val code: String,
    message: String,
    val status: Int = 400,
    val details: Map<String, Long> = emptyMap(),
) : RuntimeException(message)

fun validation(message: String): Nothing = throw AppException(ErrorCodes.VALIDATION, message)

fun notFound(what: String): Nothing = throw AppException(ErrorCodes.NOT_FOUND, "$what not found", 404)

const val MAX_NAME_LENGTH = 200

/** Trims and validates a display name (F-1, F-2). */
fun cleanName(name: String): String {
    val cleaned = name.trim().replace(Regex("\\s+"), " ")
    if (cleaned.isEmpty()) throw AppException(ErrorCodes.NAME_REQUIRED, "Name is required")
    if (cleaned.length > MAX_NAME_LENGTH) validation("Name is longer than $MAX_NAME_LENGTH characters")
    return cleaned
}

fun cleanOptionalText(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

fun parseDate(value: String): LocalDate = try {
    LocalDate.parse(value)
} catch (e: DateTimeParseException) {
    validation("Invalid date '$value', expected YYYY-MM-DD")
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Parses HH:MM or HH:MM:SS and normalizes to HH:MM. */
fun normalizeTime(value: String): String = try {
    LocalTime.parse(value).format(timeFormat)
} catch (e: DateTimeParseException) {
    validation("Invalid time '$value', expected HH:MM")
}

fun requirePositive(value: Double?, field: String) {
    if (value != null && (!value.isFinite() || value <= 0.0)) validation("$field must be a positive number")
}

fun requireNonNegative(value: Double?, field: String) {
    if (value != null && (!value.isFinite() || value < 0.0)) validation("$field must be zero or more")
}
