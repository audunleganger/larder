package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.HistoryDay
import com.caloriecompanion.shared.api.HistoryView
import com.caloriecompanion.shared.api.NutrientSummary
import com.caloriecompanion.shared.api.TargetStatus
import com.caloriecompanion.shared.domain.NutritionCalculator
import com.caloriecompanion.shared.domain.TargetTimeline
import com.caloriecompanion.shared.domain.parseDate
import com.caloriecompanion.shared.domain.validation
import java.time.temporal.ChronoUnit

/** Per-day totals and summary statistics over a date range (H-1 … H-4). */
class HistoryService(private val db: CalorieCompanionDatabase, private val userId: Long) {

    fun history(from: String, to: String): HistoryView {
        val start = parseDate(from)
        val end = parseDate(to)
        if (end < start) validation("'to' is before 'from'")
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) validation("Range is longer than $MAX_DAYS days")

        val catalog = loadCatalog(db, userId)
        val calculator = NutritionCalculator(catalog)
        val timeline = TargetTimeline(TargetService(db, userId).list())
        val byDate = db.entryQueries.selectEntriesInRange(userId, start.toString(), end.toString())
            .executeAsList()
            .groupBy { it.local_date }

        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.map { date ->
            val key = date.toString()
            val rows = byDate[key].orEmpty()
            val nutrition = rows.map { calculator.entryNutrition(catalog.foods.getValue(it.food_id), it.unit_id, it.quantity) }
            HistoryDay(key, rows.size, calculator.totals(nutrition, timeline.on(key)))
        }.toList()

        // Days without entries are gaps, not zeros (H-4). The same goes for logged days where no entry
        // has a value for the nutrient: counting them as 0 would drag averages down.
        val summary = catalog.displayedNutrients.map { nutrient ->
            val totals = days
                .filter { it.entryCount > 0 }
                .map { day -> day to day.totals.first { it.nutrientId == nutrient.id } }
                .filter { (day, total) -> total.missingCount < day.entryCount }
                .map { (_, total) -> total }
            val amounts = totals.map { it.amount }
            NutrientSummary(
                nutrientId = nutrient.id,
                average = amounts.takeIf { it.isNotEmpty() }?.average(),
                min = amounts.minOrNull(),
                max = amounts.maxOrNull(),
                loggedDays = totals.size,
                daysWithTarget = totals.count { it.status != TargetStatus.NONE },
                daysWithinTarget = totals.count { it.status == TargetStatus.WITHIN },
            )
        }
        return HistoryView(start.toString(), end.toString(), days, summary)
    }

    companion object {
        const val MAX_DAYS = 3660L
    }
}
