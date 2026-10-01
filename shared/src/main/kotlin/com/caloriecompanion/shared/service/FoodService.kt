package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.CompositeDetail
import com.caloriecompanion.shared.api.CompositeInput
import com.caloriecompanion.shared.api.FoodDetail
import com.caloriecompanion.shared.api.FoodDto
import com.caloriecompanion.shared.api.FoodEntryRef
import com.caloriecompanion.shared.api.FoodImageData
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodRef
import com.caloriecompanion.shared.api.FoodRefDefault
import com.caloriecompanion.shared.api.FoodSummary
import com.caloriecompanion.shared.api.IngredientView
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.Catalog
import com.caloriecompanion.shared.domain.Composites
import com.caloriecompanion.shared.domain.FoodDef
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
                FoodSummary(
                    food.id, food.displayName(language), food.archived, food.refAmount, food.refUnitId,
                    food.nutrients.size, food.imageVersion, composite = food.composite != null,
                )
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
        val usedIn = catalog.foods.values
            .filter { other -> other.composite?.ingredients.orEmpty().any { it.foodId == id } }
            .map { FoodRef(it.id, it.displayName(language), it.archived) }
            .sortedBy { it.name.lowercase() }
        val loggedAsItemsOn = if (food.composite != null) db.entryQueries.selectDatesViaFood(userId, id).executeAsList() else emptyList()
        return FoodDetail(
            food.toDto(language), UnitResolver(catalog).usableUnits(food, language), entries,
            compositeDetail(catalog, food), usedIn, loggedAsItemsOn,
        )
    }

    private fun compositeDetail(catalog: Catalog, food: FoodDef): CompositeDetail? {
        val composite = food.composite ?: return null
        return CompositeDetail(
            ingredients = composite.ingredients.mapIndexed { index, ingredient ->
                val part = catalog.foods[ingredient.foodId]
                val unit = catalog.units[ingredient.unitId]
                IngredientView(
                    foodId = ingredient.foodId,
                    foodName = part?.displayName(language) ?: "?",
                    foodImageVersion = part?.imageVersion,
                    unitId = ingredient.unitId,
                    unitName = unit?.displayName(language) ?: "?",
                    unitPluralSuffix = unit?.displayPluralSuffix(language).orEmpty(),
                    quantity = ingredient.quantity,
                    unresolved = index in composite.unresolved,
                    grams = composite.grams.getOrNull(index),
                )
            },
            yieldAmount = food.refAmount,
            yieldUnitId = food.refUnitId,
            yieldAutomatic = composite.yieldAmount == null,
            totalGrams = composite.totalGrams,
            nutrients = catalog.nutrients.mapNotNull { n -> food.nutrients[n.id]?.let { FoodNutrientValue(n.id, it) } },
            logAsWhole = composite.logAsWhole,
        )
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
        clean.composite?.let { writeComposite(id, it) }
        if (rememberRef) rememberRef(clean)
        get(id)
    }

    /** Replaces all fields, unit links and nutrient values of a food. */
    fun update(id: Long, input: FoodInput, rememberRef: Boolean = true): FoodDto = db.transactionWithResult {
        val catalog = loadCatalog(db, userId)
        val before = catalog.foods[id] ?: notFound("Food")
        val clean = validate(catalog, input, selfId = id)
        val beforeRef = before.composite?.let { it.manualRefAmount to it.manualRefUnitId } ?: (before.refAmount to before.refUnitId)
        // Only a changed reference counts as "used": editing an old food's notes shouldn't reset the default.
        if (rememberRef && (clean.refAmount to clean.refUnitId) != beforeRef) rememberRef(clean)
        queries.updateFood(clean.name, normalizeName(clean.name), clean.refAmount, clean.refUnitId, clean.notes, id, userId)
        queries.deleteFoodUnits(id)
        queries.deleteFoodNutrients(id)
        writeRelations(id, clean)
        clean.translations?.let { writeTranslations(id, it) }
        clean.composite?.let { writeComposite(id, it) }
        get(id)
    }

    /**
     * Sets what a food is made of (F-10); an empty ingredient list makes it a plain food again.
     * Used for import, where ingredients can only be linked once all foods exist.
     */
    fun setComposite(id: Long, input: CompositeInput): FoodDto = db.transactionWithResult {
        val catalog = loadCatalog(db, userId)
        if (id !in catalog.foods) notFound("Food")
        writeComposite(id, validateComposite(catalog, input, id))
        get(id)
    }

    private fun writeComposite(id: Long, input: CompositeInput) {
        queries.deleteFoodIngredients(id)
        input.ingredients.forEachIndexed { position, it -> queries.insertFoodIngredient(id, position.toLong(), it.foodId, it.unitId, it.quantity) }
        queries.updateFoodComposite(input.yieldAmount, input.yieldUnitId, input.logAsWhole, id, userId)
    }

    private fun validateComposite(catalog: Catalog, input: CompositeInput, selfId: Long?): CompositeInput {
        if (input.ingredients.size > MAX_INGREDIENTS) validation("A food can have at most $MAX_INGREDIENTS ingredients")
        for (ingredient in input.ingredients) {
            val part = catalog.foods[ingredient.foodId] ?: notFound("Ingredient")
            if (ingredient.unitId !in catalog.units) notFound("Unit")
            if (!ingredient.quantity.isFinite() || ingredient.quantity <= 0) validation("Ingredient amounts must be positive")
            if (selfId != null && Composites.contains(catalog.foods, ingredient.foodId, selfId)) {
                validation(if (ingredient.foodId == selfId) "A food can't be an ingredient of itself" else "'${part.name}' already contains this food")
            }
        }
        if ((input.yieldAmount == null) != (input.yieldUnitId == null)) validation("How much it makes needs both an amount and a unit")
        requirePositive(input.yieldAmount, "How much it makes")
        input.yieldUnitId?.let { if (it !in catalog.units) notFound("Unit") }
        return input
    }

    fun setArchived(id: Long, archived: Boolean): FoodDto {
        get(id)
        queries.setFoodArchived(archived, id, userId)
        return get(id)
    }

    /** Deletes a food without entries that isn't an ingredient (F-8); otherwise fails with REFERENCED. */
    fun delete(id: Long) = db.transaction {
        get(id)
        val entries = queries.countFoodEntryRefs(id).executeAsOne()
        val composites = queries.countFoodIngredientRefs(id).executeAsOne()
        if (entries > 0 || composites > 0) {
            throw AppException(
                ErrorCodes.REFERENCED, "Food is in use; archive it instead", 409,
                mapOf("entries" to entries, "composites" to composites),
            )
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
        return input.copy(
            name = name,
            notes = cleanOptionalText(input.notes),
            translations = translations,
            composite = input.composite?.let { validateComposite(catalog, it, selfId) },
        )
    }

    companion object {
        const val MAX_INGREDIENTS = 100
    }
}
