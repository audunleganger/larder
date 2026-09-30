package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodRef
import com.caloriecompanion.shared.api.UnitDetail
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.UnitDef
import com.caloriecompanion.shared.domain.UnitResolver
import com.caloriecompanion.shared.domain.cleanName
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.domain.validation
import com.caloriecompanion.shared.normalizeName

class UnitService(private val db: CalorieCompanionDatabase, private val userId: Long) {
    private val queries = db.quantityUnitQueries

    fun list(includeArchived: Boolean = false): List<UnitDto> =
        all().filter { includeArchived || !it.archived }.map { it.toDto() }

    fun get(id: Long): UnitDto = find(id).toDto()

    fun detail(id: Long): UnitDetail {
        val catalog = loadCatalog(db, userId)
        val unit = catalog.units[id] ?: notFound("Unit")
        val resolver = UnitResolver(catalog)
        val explicit = catalog.foods.values.filter { food ->
            food.refUnitId == id || food.links.any { it.unitId == id || it.equalsUnitId == id }
        }
        val implicit = if (unit.kind.isStandard) {
            catalog.foods.values.filter { food ->
                food !in explicit && resolver.usableUnits(food).any { it.unitId == id && !it.explicit }
            }
        } else emptyList()
        return UnitDetail(
            unit = unit.toDto(),
            foods = explicit.map { FoodRef(it.id, it.name, it.archived) }.sortedBy { it.name.lowercase() },
            implicitFoods = implicit.map { FoodRef(it.id, it.name, it.archived) }.sortedBy { it.name.lowercase() },
            dates = queries.selectUnitDates(userId, id).executeAsList(),
        )
    }

    fun create(input: UnitInput, archived: Boolean = false): UnitDto = db.transactionWithResult {
        val name = cleanName(input.name)
        val factor = validFactor(input.kind, input.baseFactor)
        ensureNameFree(name, exceptId = null)
        queries.insertUnit(userId, name, normalizeName(name), input.kind.dbValue(), factor, archived)
        val id = db.appUserQueries.lastInsertRowId().executeAsOne()
        find(id).toDto()
    }

    fun update(id: Long, input: UnitInput): UnitDto = db.transactionWithResult {
        find(id)
        val name = cleanName(input.name)
        val factor = validFactor(input.kind, input.baseFactor)
        ensureNameFree(name, exceptId = id)
        queries.updateUnit(name, normalizeName(name), input.kind.dbValue(), factor, id, userId)
        find(id).toDto()
    }

    fun setArchived(id: Long, archived: Boolean): UnitDto {
        find(id)
        queries.setUnitArchived(archived, id, userId)
        return find(id).toDto()
    }

    /** Deletes an unreferenced unit (U-7); otherwise fails with REFERENCED. */
    fun delete(id: Long) = db.transaction {
        find(id)
        val entries = queries.countUnitEntryRefs(id).executeAsOne()
        val foods = queries.countUnitFoodRefs(id).executeAsOne()
        if (entries > 0 || foods > 0) {
            throw AppException(
                ErrorCodes.REFERENCED, "Unit is in use; archive it instead", 409,
                mapOf("entries" to entries, "foods" to foods),
            )
        }
        queries.deleteUnit(id, userId)
    }

    private fun all(): List<UnitDef> = queries.selectUnits(userId).executeAsList().map { it.toDef() }

    private fun find(id: Long): UnitDef = all().firstOrNull { it.id == id } ?: notFound("Unit")

    private fun ensureNameFree(name: String, exceptId: Long?) {
        val existing = queries.selectUnitByNorm(userId, normalizeName(name)).executeAsOneOrNull()
        if (existing != null && existing.id != exceptId) {
            throw AppException(ErrorCodes.NAME_TAKEN, "A unit named '${existing.name}' already exists", 409)
        }
    }

    private fun validFactor(kind: UnitKind, factor: Double?): Double? {
        if (!kind.isStandard) return null
        if (factor == null || !factor.isFinite() || factor <= 0.0) {
            validation("Mass and volume units need a positive size in g or ml")
        }
        return factor
    }
}
