package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.db.Entry
import com.caloriecompanion.shared.api.DayView
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.EntryView
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.PreviewResult
import com.caloriecompanion.shared.domain.Catalog
import com.caloriecompanion.shared.domain.NutritionCalculator
import com.caloriecompanion.shared.domain.TargetTimeline
import com.caloriecompanion.shared.domain.UnitResolver
import com.caloriecompanion.shared.domain.cleanOptionalText
import com.caloriecompanion.shared.domain.normalizeTime
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.domain.parseDate
import com.caloriecompanion.shared.domain.validation

/** [language]: the reader's language, for display names (L-5). */
class EntryService(
    private val db: CalorieCompanionDatabase,
    private val userId: Long,
    private val now: () -> Long = System::currentTimeMillis,
    private val language: String? = null,
) {
    private val queries = db.entryQueries

    /** Entries and totals for one day (D-1, E-3, T-2). */
    fun day(date: String): DayView {
        val day = parseDate(date).toString()
        val catalog = loadCatalog(db, userId)
        val calculator = NutritionCalculator(catalog)
        val rows = queries.selectEntriesInRange(userId, day, day).executeAsList()
        val nutrition = rows.map { calculator.entryNutrition(catalog.foods.getValue(it.food_id), it.unit_id, it.quantity) }
        val targets = TargetTimeline(TargetService(db, userId).list()).on(day)
        return DayView(
            date = day,
            entries = rows.zip(nutrition) { row, n -> row.toView(catalog, n.toList(catalog.displayedNutrients), n.unresolved, language) },
            totals = calculator.totals(nutrition, targets),
        )
    }

    fun get(id: Long): EntryView {
        val row = queries.selectEntry(id, userId).executeAsOneOrNull() ?: notFound("Entry")
        return view(loadCatalog(db, userId), row)
    }

    /** Live preview of an entry's nutrients before saving (E-2). */
    fun preview(input: PreviewInput): PreviewResult {
        val catalog = loadCatalog(db, userId)
        val food = catalog.foods[input.foodId] ?: notFound("Food")
        if (input.unitId !in catalog.units) notFound("Unit")
        if (!input.quantity.isFinite() || input.quantity <= 0) validation("Quantity must be a positive number")
        val nutrition = NutritionCalculator(catalog).entryNutrition(food, input.unitId, input.quantity)
        return PreviewResult(nutrition.toList(catalog.displayedNutrients), nutrition.unresolved)
    }

    fun create(input: EntryInput): EntryView = db.transactionWithResult {
        val clean = validate(input)
        val timestamp = now()
        queries.insertEntry(
            userId, clean.foodId, clean.unitId, clean.quantity, clean.date, clean.time, clean.note, timestamp, timestamp,
        )
        get(db.appUserQueries.lastInsertRowId().executeAsOne())
    }

    fun update(id: Long, input: EntryInput): EntryView = db.transactionWithResult {
        queries.selectEntry(id, userId).executeAsOneOrNull() ?: notFound("Entry")
        val clean = validate(input)
        queries.updateEntry(clean.foodId, clean.unitId, clean.quantity, clean.date, clean.time, clean.note, now(), id, userId)
        get(id)
    }

    fun delete(id: Long) {
        queries.selectEntry(id, userId).executeAsOneOrNull() ?: notFound("Entry")
        queries.deleteEntry(id, userId)
    }

    /**
     * Validates an entry. Any unit may be used: if it isn't usable for the food yet, it is linked to the
     * food without a size, and the entry is flagged incomplete until the size is filled in (C-3).
     */
    private fun validate(input: EntryInput): EntryInput {
        val catalog = loadCatalog(db, userId)
        val food = catalog.foods[input.foodId] ?: notFound("Food")
        if (input.unitId !in catalog.units) notFound("Unit")
        if (!input.quantity.isFinite() || input.quantity <= 0) validation("Quantity must be a positive number")
        val date = parseDate(input.date).toString()
        val time = normalizeTime(input.time)
        if (!UnitResolver(catalog).isUsable(food, input.unitId)) {
            db.foodQueries.insertFoodUnit(food.id, input.unitId, null, null)
        }
        return input.copy(date = date, time = time, note = cleanOptionalText(input.note))
    }

    private fun view(catalog: Catalog, row: Entry): EntryView {
        val n = NutritionCalculator(catalog).entryNutrition(catalog.foods.getValue(row.food_id), row.unit_id, row.quantity)
        return row.toView(catalog, n.toList(catalog.displayedNutrients), n.unresolved, language)
    }
}

internal fun Entry.toView(
    catalog: Catalog,
    nutrients: List<com.caloriecompanion.shared.api.NutrientAmount>,
    unresolved: Boolean,
    language: String?,
) = EntryView(
    id = id,
    foodId = food_id,
    foodName = catalog.foods[food_id]?.displayName(language) ?: "?",
    unitId = unit_id,
    unitName = catalog.units[unit_id]?.displayName(language) ?: "?",
    unitPluralSuffix = catalog.units[unit_id]?.displayPluralSuffix(language).orEmpty(),
    quantity = quantity,
    date = local_date,
    time = local_time,
    note = note,
    nutrients = nutrients,
    unresolved = unresolved,
)
