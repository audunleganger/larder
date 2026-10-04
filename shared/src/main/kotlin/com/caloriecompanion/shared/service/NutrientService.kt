package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.MetadataInput
import com.caloriecompanion.shared.api.NameTranslation
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

/**
 * Nutrients, shared by all users of the server; [userId] is the user acting. Everyone chooses which
 * nutrients they show and in which order; only a nutrient's maker and admins can change it.
 * [language]: the reader's language, for display names (L-5).
 */
class NutrientService(
    private val db: CalorieCompanionDatabase,
    private val userId: Long,
    private val language: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val queries = db.nutrientQueries
    private val reader by lazy { Reader.of(db, userId, language) }

    /** The nutrients the user shows, in their order, or [includeHidden] all of them (hidden ones last). */
    fun list(includeHidden: Boolean = false): List<NutrientDto> =
        all().filter { includeHidden || !it.hidden }.map { it.toDto(reader) }

    fun get(id: Long): NutrientDto = find(id).toDto(reader)

    fun detail(id: Long, entryLimit: Int = 500): NutrientDetail {
        val catalog = loadCatalog(db, userId)
        val nutrient = catalog.nutrientsById[id] ?: notFound("Nutrient")
        val calculator = NutritionCalculator(catalog)
        val foods = catalog.foods.values
            .filter { id in it.nutrients }
            .map { food ->
                NutrientFoodValue(
                    foodId = food.id,
                    foodName = food.displayName(language),
                    foodArchived = food.archived,
                    amount = food.nutrients.getValue(id),
                    refAmount = food.refAmount,
                    refUnitName = food.refUnitId?.let { catalog.units[it]?.displayName(language) },
                )
            }
            .sortedBy { it.foodName.lowercase() }
        val rows = db.entryQueries.selectEntriesForNutrient(userId, id, entryLimit.toLong() + 1).executeAsList()
        val entries = rows.take(entryLimit).map { entry ->
            val food = catalog.foods.getValue(entry.food_id)
            val value = food.nutrients[id]
            val factor = calculator.factor(food, entry.unit_id, entry.quantity)
            val unit = catalog.units[entry.unit_id]
            NutrientEntryRef(
                entryId = entry.id,
                date = entry.local_date,
                time = entry.local_time,
                foodId = food.id,
                foodName = food.displayName(language),
                quantity = entry.quantity,
                unitName = unit?.displayName(language) ?: "?",
                amount = if (factor != null && value != null) factor * value else null,
                unitPlural = unit?.displayPlural(language).orEmpty(),
            )
        }
        return NutrientDetail(nutrient.toDto(reader), foods, entries, entriesTruncated = rows.size > entryLimit)
    }

    /**
     * Creates a nutrient owned by the user and shows it for them, last. Names are unique across the
     * server: a taken name fails with NAME_TAKEN and the existing nutrient's id, so it can be shown instead.
     * [createdAt] and [updatedAt]: as in an imported file; by default it's made now and never changed.
     */
    fun create(input: NutrientInput, hidden: Boolean = false, createdAt: Long? = null, updatedAt: Long? = null): NutrientDto = db.transactionWithResult {
        val clean = validate(input, selfId = null)
        val id = insert(clean, builtIn = false, createdAt ?: now(), updatedAt)
        if (!hidden) show(id)
        find(id).toDto(reader)
    }

    /** A null translation list in [input] leaves the translations unchanged. */
    fun update(id: Long, input: NutrientInput): NutrientDto = update(id, input, touch = true)

    /** [touch]: whether this counts as a change (the import linking a new nutrient to its group doesn't). */
    internal fun update(id: Long, input: NutrientInput, touch: Boolean): NutrientDto = db.transactionWithResult {
        editable(id)
        val clean = validate(input, selfId = id)
        queries.updateNutrient(
            clean.name, normalizeName(clean.name), clean.measureUnit,
            clean.displayPrecision.toLong(), clean.parentId, id,
        )
        clean.translations?.let { writeTranslations(id, it) }
        if (touch) queries.touchNutrient(now(), userId, id)
        // A changed parent moves the nutrient into (or out of) a group; store the grouped order.
        storeOrder(all())
        find(id).toDto(reader)
    }

    /** Corrects who made the nutrient and when, and when and by whom it was last changed; admins only. */
    fun setMetadata(id: Long, input: MetadataInput): NutrientDto = db.transactionWithResult {
        MetadataRules.requireAdmin(reader)
        find(id)
        val metadata = MetadataRules.resolve(db, input)
        queries.setNutrientMetadata(metadata.ownerId, metadata.createdAt, metadata.updatedAt, metadata.updatedBy, id)
        find(id).toDto(reader)
    }

    /**
     * Sets the user's display order (N-3). Shown nutrients not listed keep their relative order after the
     * listed ones. The result is always grouped: a main nutrient's position moves its whole group, and
     * sub-nutrients are only ordered among their siblings.
     */
    fun reorder(ids: List<Long>): List<NutrientDto> = db.transactionWithResult {
        val existing = all()
        val known = existing.map { it.id }.toSet()
        if (ids.any { it !in known }) notFound("Nutrient")
        if (ids.toSet().size != ids.size) validation("Duplicate nutrient in order")
        val position = (ids + existing.map { it.id }.filter { it !in ids }).withIndex().associate { it.value to it.index }
        storeOrder(existing.map { it.copy(sortOrder = position.getValue(it.id)) })
        list(includeHidden = true)
    }

    private fun storeOrder(nutrients: List<NutrientDef>) {
        nutrients.filter { !it.hidden }.inDisplayOrder()
            .forEachIndexed { index, n -> queries.setShownNutrientOrder(index.toLong(), userId, n.id) }
    }

    /**
     * Hides the nutrient from the user's totals, history and lists, or shows it again at the end of its
     * group. Anyone can, for any nutrient; foods keep their values either way.
     */
    fun setHidden(id: Long, hidden: Boolean): NutrientDto = db.transactionWithResult {
        find(id)
        if (hidden) queries.hideNutrient(userId, id) else show(id)
        find(id).toDto(reader)
    }

    private fun show(id: Long) {
        queries.showNutrient(userId, id, (queries.maxShownNutrientOrder(userId).executeAsOne().max ?: -1) + 1)
    }

    /** Deletes a nutrient no one uses (N-5); otherwise fails with REFERENCED. */
    fun delete(id: Long) = db.transaction {
        editable(id)
        val foods = queries.countNutrientFoodRefs(id).executeAsOne()
        val targets = queries.countNutrientTargetRefs(id).executeAsOne()
        if (foods > 0 || targets > 0) {
            throw AppException(
                ErrorCodes.REFERENCED, "Nutrient is in use; hide it instead", 409,
                mapOf("foods" to foods, "targets" to targets),
            )
        }
        queries.deleteNutrient(id)
    }

    /** Inserts a validated nutrient owned by the user, last in the default order; for [create] and the built-in nutrients. */
    internal fun insert(clean: NutrientInput, builtIn: Boolean, createdAt: Long = now(), updatedAt: Long? = null): Long {
        val sortOrder = (queries.maxNutrientSortOrder().executeAsOne().max ?: -1) + 1
        queries.insertNutrient(
            userId, clean.name, normalizeName(clean.name), clean.measureUnit,
            clean.displayPrecision.toLong(), sortOrder, clean.parentId, builtIn, createdAt, updatedAt,
        )
        val id = db.appUserQueries.lastInsertRowId().executeAsOne()
        writeTranslations(id, clean.translations.orEmpty())
        return id
    }

    private fun all(): List<NutrientDef> = loadNutrients(db, userId)

    private fun editable(id: Long): NutrientDef = find(id).also {
        if (!it.canEdit(userId, reader.isAdmin)) {
            val who = if (it.builtIn) "an admin" else "${it.ownerName} or an admin"
            throw AppException(ErrorCodes.FORBIDDEN, "Only $who can change '${it.name}'", 403)
        }
    }

    private fun writeTranslations(id: Long, translations: List<NameTranslation>) {
        queries.deleteNutrientTranslations(id)
        translations.forEach { queries.insertNutrientTranslation(id, it.locale, it.name, normalizeName(it.name)) }
    }

    private fun find(id: Long): NutrientDef = all().firstOrNull { it.id == id } ?: notFound("Nutrient")

    internal fun validate(input: NutrientInput, selfId: Long?): NutrientInput {
        val name = cleanName(input.name)
        val translations = input.translations?.let { NameRules.cleanTranslations(it, withPlural = false) }
        val current = selfId?.let { id -> all().firstOrNull { it.id == id } }?.translations?.values.orEmpty()
        NameRules.ensureFree(listOf(name) + (translations ?: current).map { it.name }, selfId, all(), "nutrient")
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
        return NutrientInput(name, measureUnit, input.displayPrecision, parentId, translations)
    }
}
