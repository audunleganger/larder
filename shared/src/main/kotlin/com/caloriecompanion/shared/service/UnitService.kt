package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodRef
import com.caloriecompanion.shared.api.MetadataInput
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.UnitDetail
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.UnitDef
import com.caloriecompanion.shared.domain.UnitResolver
import com.caloriecompanion.shared.domain.cleanName
import com.caloriecompanion.shared.domain.defaultPlural
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.domain.unitOrder
import com.caloriecompanion.shared.domain.validation
import com.caloriecompanion.shared.normalizeName

/**
 * Units, shared by all users of the server; [userId] is the user acting. Everyone sees and uses every
 * unit and chooses which ones they show; only a unit's maker and admins can change it.
 * [language]: the reader's language, for display names (L-5).
 */
class UnitService(
    private val db: CalorieCompanionDatabase,
    private val userId: Long,
    private val language: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val queries = db.quantityUnitQueries
    private val reader by lazy { Reader.of(db, userId, language) }

    /** The units the user shows, or [includeHidden] all of them, in the user's order (U-10; see [unitOrder]). */
    fun list(includeHidden: Boolean = false): List<UnitDto> =
        all().filter { includeHidden || !it.hidden }.sortedWith(unitOrder(language)).map { it.toDto(reader) }

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
     * [createdAt] and [updatedAt]: as in an imported file; by default it's made now and never changed.
     */
    fun create(input: UnitInput, hidden: Boolean = false, createdAt: Long? = null, updatedAt: Long? = null): UnitDto = db.transactionWithResult {
        val name = cleanName(input.name)
        val factor = validFactor(input.kind, input.baseFactor)
        val translations = NameRules.cleanTranslations(input.translations.orEmpty(), withPlural = true)
        NameRules.ensureFree(listOf(name) + translations.map { it.name }, null, all(), "unit")
        val plural = input.plural?.let(NameRules::cleanPlural) ?: defaultPlural(name, input.kind, language)
        val id = insert(name, input.kind, factor, plural, translations, builtIn = false, createdAt ?: now(), updatedAt)
        if (!hidden) show(id)
        find(id).toDto(reader)
    }

    /** A null plural or translation list in [input] leaves it unchanged. */
    fun update(id: Long, input: UnitInput): UnitDto = db.transactionWithResult {
        val before = editable(id)
        val name = cleanName(input.name)
        val factor = validFactor(input.kind, input.baseFactor)
        val translations = input.translations?.let { NameRules.cleanTranslations(it, withPlural = true) }
        NameRules.ensureFree(listOf(name) + (translations ?: before.translations.values).map { it.name }, id, all(), "unit")
        val plural = input.plural?.let(NameRules::cleanPlural) ?: before.plural
        queries.updateUnit(name, normalizeName(name), input.kind.dbValue(), factor, plural, id)
        if (translations != null) writeTranslations(id, translations)
        queries.touchUnit(now(), userId, id)
        find(id).toDto(reader)
    }

    /** Corrects who made the unit and when, and when and by whom it was last changed; admins only. */
    fun setMetadata(id: Long, input: MetadataInput): UnitDto = db.transactionWithResult {
        MetadataRules.requireAdmin(reader)
        find(id)
        val metadata = MetadataRules.resolve(db, input)
        queries.setUnitMetadata(metadata.ownerId, metadata.createdAt, metadata.updatedAt, metadata.updatedBy, id)
        find(id).toDto(reader)
    }

    /**
     * Hides the unit from the user's lists and pickers, or shows it again: last, if they have set an
     * order. Anyone can, for any unit.
     */
    fun setHidden(id: Long, hidden: Boolean): UnitDto = db.transactionWithResult {
        find(id)
        if (hidden) queries.hideUnit(userId, id) else show(id)
        find(id).toDto(reader)
    }

    private fun show(id: Long) {
        // Without an order of their own, the units stay alphabetical.
        val last = queries.maxShownUnitOrder(userId).executeAsOne().max
        queries.showUnit(userId, id, last?.plus(1))
    }

    /**
     * Sets the user's order of the units they show (U-10). Shown units not listed keep their relative
     * order after the listed ones; hidden ones are ignored.
     */
    fun reorder(ids: List<Long>): List<UnitDto> = db.transactionWithResult {
        val existing = all()
        val known = existing.map { it.id }.toSet()
        if (ids.any { it !in known }) notFound("Unit")
        if (ids.toSet().size != ids.size) validation("Duplicate unit in order")
        val shown = existing.filter { !it.hidden }.sortedWith(unitOrder(language)).map { it.id }
        val order = ids.filter { it in shown } + shown.filter { it !in ids }
        order.forEachIndexed { index, id -> queries.setShownUnitOrder(index.toLong(), userId, id) }
        list(includeHidden = true)
    }

    /** Puts the user's units back in alphabetical order (U-10). */
    fun resetOrder(): List<UnitDto> = db.transactionWithResult {
        queries.resetShownUnitOrder(userId)
        list(includeHidden = true)
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
    internal fun insert(
        name: String,
        kind: UnitKind,
        factor: Double?,
        plural: String,
        translations: List<NameTranslation>,
        builtIn: Boolean,
        createdAt: Long = now(),
        updatedAt: Long? = null,
    ): Long {
        queries.insertUnit(userId, name, normalizeName(name), kind.dbValue(), factor, plural, builtIn, createdAt, updatedAt)
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
        translations.forEach { queries.insertUnitTranslation(id, it.locale, it.name, normalizeName(it.name), it.plural) }
    }

    private fun validFactor(kind: UnitKind, factor: Double?): Double? {
        if (!kind.isStandard) return null
        if (factor == null || !factor.isFinite() || factor <= 0.0) {
            validation("Mass and volume units need a positive size in g or ml")
        }
        return factor
    }
}
