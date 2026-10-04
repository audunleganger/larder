package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodRef
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.UnitDetail
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.UnitDef
import com.caloriecompanion.shared.domain.UnitResolver
import com.caloriecompanion.shared.domain.cleanName
import com.caloriecompanion.shared.domain.defaultPluralSuffix
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.domain.validation
import com.caloriecompanion.shared.normalizeName

/**
 * Units, shared by all users of the server; [userId] is the user acting. Everyone sees and uses every
 * unit and chooses which ones they show; only a unit's maker and admins can change it.
 * [language]: the reader's language, for display names (L-5).
 */
class UnitService(private val db: CalorieCompanionDatabase, private val userId: Long, private val language: String? = null) {
    private val queries = db.quantityUnitQueries
    private val reader by lazy { Reader.of(db, userId, language) }

    /** The units the user shows, or [includeHidden] all of them. */
    fun list(includeHidden: Boolean = false): List<UnitDto> =
        all().filter { includeHidden || !it.hidden }.map { it.toDto(reader) }

    fun get(id: Long): UnitDto = find(id).toDto(reader)

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
            unit = unit.toDto(reader),
            foods = explicit.map { FoodRef(it.id, it.displayName(language), it.archived) }.sortedBy { it.name.lowercase() },
            implicitFoods = implicit.map { FoodRef(it.id, it.displayName(language), it.archived) }.sortedBy { it.name.lowercase() },
            dates = queries.selectUnitDates(userId, id).executeAsList(),
        )
    }

    /**
     * Creates a unit owned by the user and shows it for them. Names are unique across the server: a
     * taken name fails with NAME_TAKEN and the existing unit's id, so it can be shown instead.
     */
    fun create(input: UnitInput, hidden: Boolean = false): UnitDto = db.transactionWithResult {
        val name = cleanName(input.name)
        val factor = validFactor(input.kind, input.baseFactor)
        val translations = NameRules.cleanTranslations(input.translations.orEmpty(), withSuffix = true)
        NameRules.ensureFree(listOf(name) + translations.map { it.name }, null, all(), "unit")
        val suffix = input.pluralSuffix?.let(NameRules::cleanSuffix) ?: defaultPluralSuffix(name, input.kind, language)
        val id = insert(name, input.kind, factor, suffix, translations, builtIn = false)
        if (!hidden) queries.showUnit(userId, id)
        find(id).toDto(reader)
    }

    /** A null plural ending or translation list in [input] leaves it unchanged. */
    fun update(id: Long, input: UnitInput): UnitDto = db.transactionWithResult {
        val before = editable(id)
        val name = cleanName(input.name)
        val factor = validFactor(input.kind, input.baseFactor)
        val translations = input.translations?.let { NameRules.cleanTranslations(it, withSuffix = true) }
        NameRules.ensureFree(listOf(name) + (translations ?: before.translations.values).map { it.name }, id, all(), "unit")
        val suffix = input.pluralSuffix?.let(NameRules::cleanSuffix) ?: before.pluralSuffix
        queries.updateUnit(name, normalizeName(name), input.kind.dbValue(), factor, suffix, id)
        if (translations != null) writeTranslations(id, translations)
        find(id).toDto(reader)
    }

    /** Hides the unit from the user's lists and pickers, or shows it again. Anyone can, for any unit. */
    fun setHidden(id: Long, hidden: Boolean): UnitDto {
        find(id)
        if (hidden) queries.hideUnit(userId, id) else queries.showUnit(userId, id)
        return find(id).toDto(reader)
    }

    /** Deletes a unit no one uses (U-7); otherwise fails with REFERENCED. */
    fun delete(id: Long) = db.transaction {
        editable(id)
        val entries = queries.countUnitEntryRefs(id).executeAsOne()
        val foods = queries.countUnitFoodRefs(id).executeAsOne()
        if (entries > 0 || foods > 0) {
            throw AppException(
                ErrorCodes.REFERENCED, "Unit is in use; hide it instead", 409,
                mapOf("entries" to entries, "foods" to foods),
            )
        }
        queries.deleteUnit(id)
    }

    /** Inserts a unit owned by the user, without checks; for [create] and the built-in units. */
    internal fun insert(name: String, kind: UnitKind, factor: Double?, suffix: String, translations: List<NameTranslation>, builtIn: Boolean): Long {
        queries.insertUnit(userId, name, normalizeName(name), kind.dbValue(), factor, suffix, builtIn)
        val id = db.appUserQueries.lastInsertRowId().executeAsOne()
        writeTranslations(id, translations)
        return id
    }

    private fun all(): List<UnitDef> = loadUnits(db, userId)

    private fun find(id: Long): UnitDef = all().firstOrNull { it.id == id } ?: notFound("Unit")

    private fun editable(id: Long): UnitDef = find(id).also {
        if (!it.canEdit(userId, reader.isAdmin)) {
            val who = if (it.builtIn) "an admin" else "${it.ownerName} or an admin"
            throw AppException(ErrorCodes.FORBIDDEN, "Only $who can change '${it.name}'", 403)
        }
    }

    private fun writeTranslations(id: Long, translations: List<NameTranslation>) {
        queries.deleteUnitTranslations(id)
        translations.forEach { queries.insertUnitTranslation(id, it.locale, it.name, normalizeName(it.name), it.pluralSuffix) }
    }

    private fun validFactor(kind: UnitKind, factor: Double?): Double? {
        if (!kind.isStandard) return null
        if (factor == null || !factor.isFinite() || factor <= 0.0) {
            validation("Mass and volume units need a positive size in g or ml")
        }
        return factor
    }
}
