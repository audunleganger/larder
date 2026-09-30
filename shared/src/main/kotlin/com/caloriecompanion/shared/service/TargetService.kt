package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.TargetDto
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.domain.parseDate
import com.caloriecompanion.shared.domain.requireNonNegative
import com.caloriecompanion.shared.domain.validation

/**
 * Daily targets (T-1, T-3). Each change is stored as a version effective from a date;
 * a version with neither min nor max clears the target from that date on.
 */
class TargetService(private val db: CalorieCompanionDatabase, private val userId: Long) {
    private val queries = db.targetQueries

    fun list(): List<TargetDto> = queries.selectTargets(userId).executeAsList().map { it.toDto() }

    fun set(input: TargetInput): TargetDto = db.transactionWithResult {
        val nutrients = db.nutrientQueries.selectNutrients(userId).executeAsList()
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
