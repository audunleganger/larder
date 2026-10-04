package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.MetadataInput
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.validation
import java.time.Instant
import java.time.format.DateTimeParseException

/** Who made an item and when, and when and by whom it was last changed, as stored. */
internal data class Metadata(val ownerId: Long, val createdAt: Long, val updatedAt: Long?, val updatedBy: Long?)

/** Validation of corrected metadata (admins only) and of dates in export files. */
internal object MetadataRules {
    fun requireAdmin(reader: Reader) {
        if (!reader.isAdmin) throw AppException(ErrorCodes.FORBIDDEN, "Only admins can change who made something and when", 403)
    }

    /** Checks [input] and looks up its users. */
    fun resolve(db: CalorieCompanionDatabase, input: MetadataInput): Metadata {
        validTime(input.createdAt, "Created")
        input.updatedAt?.let { validTime(it, "Last changed") }
        if (input.updatedBy != null && input.updatedAt == null) validation("Who changed it last needs a date")
        if (input.updatedAt != null && input.updatedAt < input.createdAt) validation("It can't be changed before it was made")
        return Metadata(userId(db, input.createdBy), input.createdAt, input.updatedAt, input.updatedBy?.let { userId(db, it) })
    }

    fun validTime(time: Long, what: String) {
        // Between 1970 and the year 9999; anything else is a mistake (or seconds instead of milliseconds).
        if (time !in 1..MAX_TIME) validation("$what is not a valid time")
    }

    /** An export file's time, or null if absent or unreadable (it's only informational). */
    fun parseTime(value: String?): Long? = try {
        value?.let { Instant.parse(it).toEpochMilli() }?.takeIf { it in 1..MAX_TIME }
    } catch (_: DateTimeParseException) {
        null
    }

    fun formatTime(time: Long?): String? = time?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() }

    private fun userId(db: CalorieCompanionDatabase, username: String): Long =
        db.appUserQueries.selectByUsername(username.trim()).executeAsOneOrNull()?.id ?: validation("No user named '${username.trim()}'")

    private const val MAX_TIME = 253_402_300_799_999L
}
