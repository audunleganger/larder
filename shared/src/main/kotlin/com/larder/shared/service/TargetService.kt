package com.larder.shared.service

import com.larder.db.LarderDatabase
import com.larder.shared.api.TargetDto
import com.larder.shared.api.TargetInput
import com.larder.shared.domain.notFound
import com.larder.shared.domain.parseDate
import com.larder.shared.domain.requireNonNegative
import com.larder.shared.domain.validation

/**
 * Daily targets (T-1, T-3). Each change is stored as a version effective from a date;
 * a version with neither min nor max clears the target from that date on.
 */
class TargetService(private val db: LarderDatabase, private val userId: Long) {
    private val queries = db.targetQueries

    fun list(): List<TargetDto> = queries.selectTargets(userId).executeAsList().map { it.toDto() }

    fun set(input: TargetInput): TargetDto = db.transactionWithResult {
        val nutrients = db.nutrientQueries.selectNutrients().executeAsList()
        if (nutrients.none { it.id == input.nutrientId }) notFound("Nutrient")
        requireNonNegative(input.min, "Minimum")
        requireNonNegative(input.max, "Maximum")
        if (input.min != null && input.max != null && input.min > input.max) validation("Minimum is larger than maximum")
        val from = parseDate(input.effectiveFrom).toString()
        queries.upsertTarget(userId, input.nutrientId, input.min, input.max, from)
        list().first { it.nutrientId == input.nutrientId && it.effectiveFrom == from }
    }

    fun delete(id: Long) {
        if (list().none { it.id == id }) notFound("Target")
        queries.deleteTarget(id, userId)
    }
}
