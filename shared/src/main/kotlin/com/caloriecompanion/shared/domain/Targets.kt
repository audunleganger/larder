package com.caloriecompanion.shared.domain

import com.caloriecompanion.shared.api.TargetDto

/** Resolves which target version is in effect on a date (T-3). */
class TargetTimeline(targets: List<TargetDto>) {
    private val byNutrient: Map<Long, List<TargetDto>> =
        targets.groupBy { it.nutrientId }.mapValues { (_, list) -> list.sortedBy { it.effectiveFrom } }

    /** Nutrient id -> target in effect on [date] (ISO string compare works for YYYY-MM-DD). */
    fun on(date: String): Map<Long, TargetRange> = buildMap {
        for ((nutrientId, versions) in byNutrient) {
            val current = versions.lastOrNull { it.effectiveFrom <= date } ?: continue
            if (current.min != null || current.max != null) put(nutrientId, TargetRange(current.min, current.max))
        }
    }
}
