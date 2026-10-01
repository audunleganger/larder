package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.NutrientDetail
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.NutrientEntryRef
import com.caloriecompanion.shared.api.NutrientFoodValue
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.NutrientDef
import com.caloriecompanion.shared.domain.NutritionCalculator
import com.caloriecompanion.shared.domain.cleanName
import com.caloriecompanion.shared.domain.inDisplayOrder
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.domain.validation
import com.caloriecompanion.shared.normalizeName

class NutrientService(private val db: CalorieCompanionDatabase, private val userId: Long) {
    private val queries = db.nutrientQueries

    fun list(includeArchived: Boolean = false): List<NutrientDto> =
        all().filter { includeArchived || !it.archived }.map { it.toDto() }

    fun get(id: Long): NutrientDto = find(id).toDto()

    fun detail(id: Long, entryLimit: Int = 500): NutrientDetail {
        val catalog = loadCatalog(db, userId)
        val nutrient = catalog.nutrientsById[id] ?: notFound("Nutrient")
        val calculator = NutritionCalculator(catalog)
        val foods = catalog.foods.values
            .filter { id in it.nutrients }
            .sortedBy { it.name.lowercase() }
            .map { food ->
                NutrientFoodValue(
                    foodId = food.id,
                    foodName = food.name,
                    foodArchived = food.archived,
                    amount = food.nutrients.getValue(id),
                    refAmount = food.refAmount,
                    refUnitName = food.refUnitId?.let { catalog.units[it]?.name },
                )
            }
        val rows = db.entryQueries.selectEntriesForNutrient(userId, id, entryLimit.toLong() + 1).executeAsList()
        val entries = rows.take(entryLimit).map { entry ->
            val food = catalog.foods.getValue(entry.food_id)
            val value = food.nutrients[id]
            val factor = calculator.factor(food, entry.unit_id, entry.quantity)
            NutrientEntryRef(
                entryId = entry.id,
                date = entry.local_date,
                time = entry.local_time,
                foodId = food.id,
                foodName = food.name,
                quantity = entry.quantity,
                unitName = catalog.units[entry.unit_id]?.name ?: "?",
                amount = if (factor != null && value != null) factor * value else null,
            )
        }
        return NutrientDetail(nutrient.toDto(), foods, entries, entriesTruncated = rows.size > entryLimit)
    }

    fun create(input: NutrientInput, archived: Boolean = false): NutrientDto = db.transactionWithResult {
        val clean = validate(input, selfId = null)
        val sortOrder = (queries.maxNutrientSortOrder(userId).executeAsOne().max ?: -1) + 1
        queries.insertNutrient(
            userId, clean.name, normalizeName(clean.name), clean.measureUnit,
            clean.displayPrecision.toLong(), sortOrder, clean.parentId, archived,
        )
        find(db.appUserQueries.lastInsertRowId().executeAsOne()).toDto()
    }

    fun update(id: Long, input: NutrientInput): NutrientDto = db.transactionWithResult {
        find(id)
        val clean = validate(input, selfId = id)
        queries.updateNutrient(
            clean.name, normalizeName(clean.name), clean.measureUnit,
            clean.displayPrecision.toLong(), clean.parentId, id, userId,
        )
        // A changed parent moves the nutrient into (or out of) a group; store the grouped order.
        storeOrder(all())
        find(id).toDto()
    }

    /**
     * Sets the display order (N-3). Nutrients not listed keep their relative order after the listed ones.
     * The result is always grouped: a main nutrient's position moves its whole group, and sub-nutrients
     * are only ordered among their siblings.
     */
    fun reorder(ids: List<Long>): List<NutrientDto> = db.transactionWithResult {
        val existing = all()
        val known = existing.map { it.id }.toSet()
        if (ids.any { it !in known }) notFound("Nutrient")
        if (ids.toSet().size != ids.size) validation("Duplicate nutrient in order")
        val position = (ids + existing.map { it.id }.filter { it !in ids }).withIndex().associate { it.value to it.index }
        storeOrder(existing.map { it.copy(sortOrder = position.getValue(it.id)) })
        all().map { it.toDto() }
    }

    private fun storeOrder(nutrients: List<NutrientDef>) {
        nutrients.inDisplayOrder().forEachIndexed { index, n -> queries.setNutrientSortOrder(index.toLong(), n.id, userId) }
    }

    fun setArchived(id: Long, archived: Boolean): NutrientDto {
        find(id)
        queries.setNutrientArchived(archived, id, userId)
        return find(id).toDto()
    }

    /** Deletes an unreferenced nutrient (N-5); otherwise fails with REFERENCED. */
    fun delete(id: Long) = db.transaction {
        find(id)
        val foods = queries.countNutrientFoodRefs(id).executeAsOne()
        val targets = queries.countNutrientTargetRefs(id).executeAsOne()
        if (foods > 0 || targets > 0) {
            throw AppException(
                ErrorCodes.REFERENCED, "Nutrient is in use; archive it instead", 409,
                mapOf("foods" to foods, "targets" to targets),
            )
        }
        queries.deleteNutrient(id, userId)
    }

    private fun all(): List<NutrientDef> = queries.selectNutrients(userId).executeAsList().map { it.toDef() }.inDisplayOrder()

    private fun find(id: Long): NutrientDef = all().firstOrNull { it.id == id } ?: notFound("Nutrient")

    private fun validate(input: NutrientInput, selfId: Long?): NutrientInput {
        val name = cleanName(input.name)
        val existing = queries.selectNutrientByNorm(userId, normalizeName(name)).executeAsOneOrNull()
        if (existing != null && existing.id != selfId) {
            throw AppException(ErrorCodes.NAME_TAKEN, "A nutrient named '${existing.name}' already exists", 409)
        }
        val measureUnit = input.measureUnit.trim()
        if (measureUnit.isEmpty()) validation("Measurement unit is required")
        if (measureUnit.length > 20) validation("Measurement unit is too long")
        if (input.displayPrecision !in 0..6) validation("Display precision must be between 0 and 6")
        val parentId = input.parentId
        if (parentId != null) {
            val all = all()
            val parent = all.firstOrNull { it.id == parentId } ?: notFound("Parent nutrient")
            if (parentId == selfId) validation("A nutrient can't be its own parent")
            if (parent.parentId != null) validation("The parent nutrient already has a parent; only one level is supported")
            if (selfId != null && all.any { it.parentId == selfId }) {
                validation("This nutrient has sub-nutrients and can't have a parent itself")
            }
        }
        return NutrientInput(name, measureUnit, input.displayPrecision, parentId)
    }
}
