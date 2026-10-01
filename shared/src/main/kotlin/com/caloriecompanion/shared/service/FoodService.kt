package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodDetail
import com.caloriecompanion.shared.api.FoodDto
import com.caloriecompanion.shared.api.FoodEntryRef
import com.caloriecompanion.shared.api.FoodImageData
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodRefDefault
import com.caloriecompanion.shared.api.FoodSummary
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.Catalog
import com.caloriecompanion.shared.domain.ImageRules
import com.caloriecompanion.shared.domain.StoredImage
import com.caloriecompanion.shared.domain.UnitResolver
import com.caloriecompanion.shared.domain.cleanName
import com.caloriecompanion.shared.domain.cleanOptionalText
import com.caloriecompanion.shared.domain.notFound
import com.caloriecompanion.shared.domain.requireNonNegative
import com.caloriecompanion.shared.domain.requirePositive
import com.caloriecompanion.shared.domain.validation
import com.caloriecompanion.shared.normalizeName

/** [language]: the reader's language, for display names (L-5). */
class FoodService(
    private val db: CalorieCompanionDatabase,
    private val userId: Long,
    private val language: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val queries = db.foodQueries

    /**
     * Foods matching [query] (substring, case- and diacritic-insensitive) in any of their names (L-5),
     * prefix matches first, sorted by display name.
     */
    fun list(query: String? = null, includeArchived: Boolean = false): List<FoodSummary> {
        val catalog = loadCatalog(db, userId)
        val needle = query?.trim()?.takeIf { it.isNotEmpty() }?.let(::foldForSearch)
        return catalog.foods.values
            .asSequence()
            .filter { includeArchived || !it.archived }
            .map { food -> food to (listOf(food.name) + food.translations.values.map { it.name }).map(::foldForSearch) }
            .filter { (_, names) -> needle == null || names.any { needle in it } }
            .map { (food, names) -> Triple(food, foldForSearch(food.displayName(language)), needle != null && names.none { it.startsWith(needle) }) }
            .sortedWith(compareBy({ it.third }, { it.second }))
            .map { (food, _, _) ->
                FoodSummary(food.id, food.displayName(language), food.archived, food.refAmount, food.refUnitId, food.nutrients.size, food.imageVersion)
            }
            .toList()
    }

    fun get(id: Long): FoodDto = (loadCatalog(db, userId).foods[id] ?: notFound("Food")).toDto(language)

    fun detail(id: Long): FoodDetail {
        val catalog = loadCatalog(db, userId)
        val food = catalog.foods[id] ?: notFound("Food")
        val entries = db.entryQueries.selectEntriesForFood(userId, id).executeAsList().map {
            val unit = catalog.units[it.unit_id]
            FoodEntryRef(
                it.id, it.local_date, it.local_time, it.quantity, it.unit_id,
                unit?.displayName(language) ?: "?", unit?.displayPluralSuffix(language).orEmpty(),
            )
        }
        return FoodDetail(food.toDto(language), UnitResolver(catalog).usableUnits(food, language), entries)
    }

    /**
     * The reference amount and unit a new food starts with (F-12): the ones last set on a food, or
     * 100 g when there are none yet (or that unit is gone or archived).
     */
    fun refDefault(): FoodRefDefault {
        val catalog = loadCatalog(db, userId)
        val stored = db.appUserQueries.selectFoodRefDefault(userId).executeAsOneOrNull()
        val unit = stored?.food_ref_unit_id?.let { catalog.units[it] }
        if (stored?.food_ref_amount != null && unit != null && !unit.archived) return FoodRefDefault(stored.food_ref_amount, unit.id)
        val gram = catalog.units.values.filter { it.kind == UnitKind.MASS && it.baseFactor == 1.0 && !it.archived }.minByOrNull { it.id }
        return FoodRefDefault(gram?.let { 100.0 }, gram?.id)
    }

    /** [rememberRef]: whether a reference amount set here becomes the default for new foods. */
    fun create(input: FoodInput, archived: Boolean = false, rememberRef: Boolean = true): FoodDto = db.transactionWithResult {
        val catalog = loadCatalog(db, userId)
        val clean = validate(catalog, input, selfId = null)
        queries.insertFood(userId, clean.name, normalizeName(clean.name), clean.refAmount, clean.refUnitId, clean.notes, archived)
        val id = db.appUserQueries.lastInsertRowId().executeAsOne()
        writeRelations(id, clean)
        writeTranslations(id, clean.translations.orEmpty())
        if (rememberRef) rememberRef(clean)
        get(id)
    }

    /** Replaces all fields, unit links and nutrient values of a food. */
    fun update(id: Long, input: FoodInput, rememberRef: Boolean = true): FoodDto = db.transactionWithResult {
        val catalog = loadCatalog(db, userId)
        val before = catalog.foods[id] ?: notFound("Food")
        val clean = validate(catalog, input, selfId = id)
        // Only a changed reference counts as "used": editing an old food's notes shouldn't reset the default.
        if (rememberRef && (clean.refAmount != before.refAmount || clean.refUnitId != before.refUnitId)) rememberRef(clean)
        queries.updateFood(clean.name, normalizeName(clean.name), clean.refAmount, clean.refUnitId, clean.notes, id, userId)
        queries.deleteFoodUnits(id)
        queries.deleteFoodNutrients(id)
        writeRelations(id, clean)
        clean.translations?.let { writeTranslations(id, it) }
        get(id)
    }

    fun setArchived(id: Long, archived: Boolean): FoodDto {
        get(id)
        queries.setFoodArchived(archived, id, userId)
        return get(id)
    }

    /** Deletes a food without entries (F-8); otherwise fails with REFERENCED. */
    fun delete(id: Long) = db.transaction {
        get(id)
        val entries = queries.countFoodEntryRefs(id).executeAsOne()
        if (entries > 0) {
            throw AppException(ErrorCodes.REFERENCED, "Food is in use; archive it instead", 409, mapOf("entries" to entries))
        }
        queries.deleteFood(id, userId)
    }

    /** Stores the food's photo (F-13), replacing any previous one. Clients resize it before sending. */
    fun setImage(id: Long, data: FoodImageData): FoodDto = db.transactionWithResult {
        get(id)
        val image = ImageRules.decode(data.image, data.contentType, ImageRules.MAX_IMAGE_BYTES, "image")
        val thumbnail = ImageRules.decode(data.thumbnail, data.contentType, ImageRules.MAX_THUMBNAIL_BYTES, "thumbnail")
        // The version must change even for two uploads within the same millisecond.
        val previous = queries.selectFoodImageVersions(userId).executeAsList().firstOrNull { it.food_id == id }?.updated_at ?: 0
        queries.upsertFoodImage(id, data.contentType, image, thumbnail, maxOf(now(), previous + 1))
        get(id)
    }

    fun deleteImage(id: Long): FoodDto = db.transactionWithResult {
        get(id)
        queries.deleteFoodImage(id)
        get(id)
    }

    /** The food's photo, or its [thumbnail]; null if it has none. */
    fun image(id: Long, thumbnail: Boolean): StoredImage? =
        if (thumbnail) {
            queries.selectFoodThumbnail(id, userId).executeAsOneOrNull()?.let { StoredImage(it.content_type, it.thumbnail, it.updated_at) }
        } else {
            queries.selectFoodImage(id, userId).executeAsOneOrNull()?.let { StoredImage(it.content_type, it.image, it.updated_at) }
        }

    /** The photo as stored, for export; null if none. */
    internal fun imageData(id: Long): FoodImageData? =
        queries.selectFoodImageFull(id, userId).executeAsOneOrNull()?.let {
            FoodImageData(it.content_type, ImageRules.encode(it.image), ImageRules.encode(it.thumbnail))
        }

    private fun rememberRef(input: FoodInput) {
        if (input.refAmount != null && input.refUnitId != null) {
            db.appUserQueries.updateFoodRefDefault(input.refAmount, input.refUnitId, userId)
        }
    }

    private fun writeTranslations(id: Long, translations: List<NameTranslation>) {
        queries.deleteFoodTranslations(id)
        translations.forEach { queries.insertFoodTranslation(id, it.locale, it.name, normalizeName(it.name)) }
    }

    private fun writeRelations(foodId: Long, input: FoodInput) {
        input.units.forEach { queries.insertFoodUnit(foodId, it.unitId, it.equalsAmount, it.equalsUnitId) }
        input.nutrients.forEach { queries.insertFoodNutrient(foodId, it.nutrientId, it.amount) }
    }

    private fun validate(catalog: Catalog, input: FoodInput, selfId: Long?): FoodInput {
        val name = cleanName(input.name)
        val translations = input.translations?.let { NameRules.cleanTranslations(it, withSuffix = false) }
        val current = selfId?.let { catalog.foods[it] }?.translations?.values.orEmpty()
        NameRules.ensureFree(listOf(name) + (translations ?: current).map { it.name }, selfId, catalog.foods.values, "food")
        if ((input.refAmount == null) != (input.refUnitId == null)) {
            validation("Reference amount and reference unit must be given together")
        }
        requirePositive(input.refAmount, "Reference amount")
        input.refUnitId?.let { if (it !in catalog.units) notFound("Reference unit") }

        val seenUnits = HashSet<Long>()
        for (link in input.units) {
            if (link.unitId !in catalog.units) notFound("Unit")
            if (!seenUnits.add(link.unitId)) validation("A unit is linked more than once")
            if ((link.equalsAmount == null) != (link.equalsUnitId == null)) {
                validation("A unit size needs both an amount and a unit")
            }
            requirePositive(link.equalsAmount, "Unit size")
            link.equalsUnitId?.let {
                if (it !in catalog.units) notFound("Unit")
                if (it == link.unitId) validation("A unit can't be defined in terms of itself")
            }
        }

        val seenNutrients = HashSet<Long>()
        for (value in input.nutrients) {
            if (value.nutrientId !in catalog.nutrientsById) notFound("Nutrient")
            if (!seenNutrients.add(value.nutrientId)) validation("A nutrient is given more than once")
            requireNonNegative(value.amount, "Nutrient amount")
        }
        return input.copy(name = name, notes = cleanOptionalText(input.notes), translations = translations)
    }
}
