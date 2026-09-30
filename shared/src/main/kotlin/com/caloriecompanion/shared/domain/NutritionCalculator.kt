package com.caloriecompanion.shared.domain

import com.caloriecompanion.shared.api.NutrientAmount
import com.caloriecompanion.shared.api.NutrientTotal
import com.caloriecompanion.shared.api.TargetStatus

/** Result of calculating one entry's nutrients (C-1, C-3). */
data class EntryNutrition(
    /** Displayed nutrient id -> amount, or null when missing. */
    val amounts: Map<Long, Double?>,
    /** The unit couldn't be converted to the reference amount (or the food has none). */
    val unresolved: Boolean,
) {
    fun toList(order: List<NutrientDef>): List<NutrientAmount> =
        order.map { NutrientAmount(it.id, amounts[it.id]) }
}

/** A target in effect for one nutrient on some day. */
data class TargetRange(val min: Double?, val max: Double?)

class NutritionCalculator(private val catalog: Catalog) {
    private val resolver = UnitResolver(catalog)

    /**
     * Nutrients for [quantity] x [unitId] of [food]:
     * `quantity × size of unit in reference units / reference amount × value per reference amount`.
     */
    fun entryNutrition(food: FoodDef, unitId: Long, quantity: Double): EntryNutrition {
        val factor = factor(food, unitId, quantity)
        val amounts = catalog.displayedNutrients.associate { nutrient ->
            val value = food.nutrients[nutrient.id]
            nutrient.id to if (factor != null && value != null) factor * value else null
        }
        return EntryNutrition(amounts, unresolved = factor == null)
    }

    /** Multiplier from "value per reference amount" to "value in this entry"; null if unresolvable. */
    fun factor(food: FoodDef, unitId: Long, quantity: Double): Double? {
        val refAmount = food.refAmount ?: return null
        if (refAmount <= 0.0) return null
        val perUnit = resolver.amountInRefUnit(food, unitId) ?: return null
        return quantity * perUnit / refAmount
    }

    /**
     * Sums entries per displayed nutrient. Missing values contribute 0 and are counted (C-3).
     * [targets] maps nutrient id -> target in effect.
     */
    fun totals(entries: List<EntryNutrition>, targets: Map<Long, TargetRange>): List<NutrientTotal> =
        catalog.displayedNutrients.map { nutrient ->
            var sum = 0.0
            var missing = 0
            for (entry in entries) {
                val amount = entry.amounts[nutrient.id]
                if (amount == null) missing++ else sum += amount
            }
            val target = targets[nutrient.id]
            NutrientTotal(
                nutrientId = nutrient.id,
                amount = sum,
                missingCount = missing,
                targetMin = target?.min,
                targetMax = target?.max,
                status = targetStatus(sum, target),
            )
        }

    companion object {
        fun targetStatus(amount: Double, target: TargetRange?): TargetStatus = when {
            target == null || (target.min == null && target.max == null) -> TargetStatus.NONE
            target.min != null && amount < target.min -> TargetStatus.BELOW
            target.max != null && amount > target.max -> TargetStatus.ABOVE
            else -> TargetStatus.WITHIN
        }
    }
}
