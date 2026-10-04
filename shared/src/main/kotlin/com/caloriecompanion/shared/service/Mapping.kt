package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.db.Food
import com.caloriecompanion.db.SelectNutrients
import com.caloriecompanion.db.SelectUnits
import com.caloriecompanion.shared.api.CompositeInput
import com.caloriecompanion.shared.api.FoodDto
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.Ingredient
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.TargetDto
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.Catalog
import com.caloriecompanion.shared.domain.CompositeDef
import com.caloriecompanion.shared.domain.Composites
import com.caloriecompanion.shared.domain.FoodDef
import com.caloriecompanion.shared.domain.NutrientDef
import com.caloriecompanion.shared.domain.UnitDef
import com.caloriecompanion.shared.domain.inDisplayOrder
import com.caloriecompanion.shared.domain.inLanguageOrder

internal fun UnitKind.dbValue(): String = when (this) {
    UnitKind.MASS -> "mass"
    UnitKind.VOLUME -> "volume"
    UnitKind.CUSTOM -> "custom"
}

internal fun unitKindOf(value: String): UnitKind = when (value) {
    "mass" -> UnitKind.MASS
    "volume" -> UnitKind.VOLUME
    else -> UnitKind.CUSTOM
}

internal fun SelectUnits.toDef(translations: Map<String, NameTranslation>, hidden: Boolean) = UnitDef(
    id = id,
    name = name,
    kind = unitKindOf(kind),
    baseFactor = base_factor,
    hidden = hidden,
    pluralSuffix = plural_suffix,
    translations = translations,
    ownerId = user_id,
    ownerName = owner_name,
    builtIn = built_in,
    createdAt = created_at,
    updatedAt = updated_at,
    updatedByName = updated_by_name,
)

/** [sortOrder]: the reader's position for it. */
internal fun SelectNutrients.toDef(translations: Map<String, NameTranslation>, hidden: Boolean, sortOrder: Long) = NutrientDef(
    id = id,
    name = name,
    measureUnit = measure_unit,
    displayPrecision = display_precision.toInt(),
    sortOrder = sortOrder.toInt(),
    parentId = parent_id,
    hidden = hidden,
    translations = translations,
    ownerId = user_id,
    ownerName = owner_name,
    builtIn = built_in,
    createdAt = created_at,
    updatedAt = updated_at,
    updatedByName = updated_by_name,
)

/** What a reader sees of units and nutrients: display names in their [language] (L-5), and whether they may edit them. */
internal class Reader(val userId: Long, val isAdmin: Boolean, val language: String?) {
    companion object {
        fun of(db: CalorieCompanionDatabase, userId: Long, language: String?) =
            Reader(userId, db.appUserQueries.selectById(userId).executeAsOneOrNull()?.is_admin ?: false, language)
    }
}

internal fun UnitDef.toDto(reader: Reader) = UnitDto(
    id = id,
    name = name,
    kind = kind,
    baseFactor = baseFactor,
    hidden = hidden,
    builtIn = builtIn,
    createdBy = ownerName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    updatedBy = updatedByName,
    canEdit = canEdit(reader.userId, reader.isAdmin),
    pluralSuffix = pluralSuffix,
    translations = translations.inLanguageOrder(),
    displayName = displayName(reader.language),
    displayPluralSuffix = displayPluralSuffix(reader.language),
)

internal fun NutrientDef.toDto(reader: Reader) = NutrientDto(
    id = id,
    name = name,
    measureUnit = measureUnit,
    displayPrecision = displayPrecision,
    sortOrder = sortOrder,
    parentId = parentId,
    hidden = hidden,
    builtIn = builtIn,
    createdBy = ownerName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    updatedBy = updatedByName,
    canEdit = canEdit(reader.userId, reader.isAdmin),
    translations = translations.inLanguageOrder(),
    displayName = displayName(reader.language),
)

/** A composite food's reference amount and nutrients are given as entered by hand; see [CompositeDef]. */
internal fun FoodDef.toDto(language: String?) = FoodDto(
    id = id,
    name = name,
    refAmount = if (composite != null) composite.manualRefAmount else refAmount,
    refUnitId = if (composite != null) composite.manualRefUnitId else refUnitId,
    notes = notes,
    archived = archived,
    nutrients = (composite?.manualNutrients ?: nutrients).map { (nutrientId, amount) -> FoodNutrientValue(nutrientId, amount) },
    units = links,
    translations = translations.inLanguageOrder(),
    displayName = displayName(language),
    imageVersion = imageVersion,
    composite = composite?.let { CompositeInput(it.ingredients, it.yieldAmount, it.yieldUnitId, it.logAsWhole) },
    createdAt = createdAt,
    createdBy = ownerName,
)

/** Every unit on the server, as seen by [userId]: the ones they haven't chosen to show are hidden. */
internal fun loadUnits(db: CalorieCompanionDatabase, userId: Long): List<UnitDef> {
    val translations = db.quantityUnitQueries.selectUnitTranslations().executeAsList()
        .groupBy({ it.unit_id }, { NameTranslation(it.locale, it.name, it.plural_suffix) })
        .mapValues { (_, list) -> list.associateBy { it.locale } }
    val shown = db.quantityUnitQueries.selectShownUnitIds(userId).executeAsList().toSet()
    return db.quantityUnitQueries.selectUnits().executeAsList().map { it.toDef(translations[it.id].orEmpty(), it.id !in shown) }
}

/**
 * Every nutrient on the server, as seen by [userId], in their display order (N-3): the ones they show
 * in their own order, then the hidden ones in the default order.
 */
internal fun loadNutrients(db: CalorieCompanionDatabase, userId: Long): List<NutrientDef> {
    val translations = db.nutrientQueries.selectNutrientTranslations().executeAsList()
        .groupBy({ it.nutrient_id }, { NameTranslation(it.locale, it.name) })
        .mapValues { (_, list) -> list.associateBy { it.locale } }
    val shown = db.nutrientQueries.selectShownNutrients(userId).executeAsList().associate { it.nutrient_id to it.sort_order }
    val afterShown = (shown.values.maxOrNull() ?: 0) + 1
    return db.nutrientQueries.selectNutrients().executeAsList().map {
        val order = shown[it.id]
        it.toDef(translations[it.id].orEmpty(), hidden = order == null, sortOrder = order ?: (afterShown + it.sort_order))
    }.inDisplayOrder()
}

internal fun com.caloriecompanion.db.Target.toDto() = TargetDto(id, nutrient_id, min_amount, max_amount, effective_from)

/**
 * Loads a user's whole catalog: their foods, and every unit and nutrient on the server. Catalogs are
 * small (hundreds to a few thousand rows).
 */
fun loadCatalog(db: CalorieCompanionDatabase, userId: Long): Catalog {
    val units = loadUnits(db, userId)
    val nutrients = loadNutrients(db, userId)
    val foodNames = db.foodQueries.selectFoodTranslations(userId).executeAsList()
        .groupBy({ it.food_id }, { NameTranslation(it.locale, it.name) })
    val imageVersions = db.foodQueries.selectFoodImageVersions(userId).executeAsList().associate { it.food_id to it.updated_at }
    val ingredients = db.foodQueries.selectFoodIngredients(userId).executeAsList()
        .groupBy({ it.food_id }, { Ingredient(it.ingredient_id, it.unit_id, it.quantity) })
    val links = db.foodQueries.selectFoodUnits(userId).executeAsList().groupBy { it.food_id }
    val values = db.foodQueries.selectFoodNutrients(userId).executeAsList().groupBy { it.food_id }
    val ownerName = db.appUserQueries.selectById(userId).executeAsOneOrNull()?.username.orEmpty()
    val foods = db.foodQueries.selectFoods(userId).executeAsList().map { food: Food ->
        val manualNutrients = values[food.id].orEmpty().associate { it.nutrient_id to it.amount }
        FoodDef(
            id = food.id,
            name = food.name,
            refAmount = food.ref_amount,
            refUnitId = food.ref_unit_id,
            notes = food.notes,
            archived = food.archived,
            nutrients = manualNutrients,
            links = links[food.id].orEmpty().map { FoodUnitLink(it.unit_id, it.equals_amount, it.equals_unit_id) },
            translations = foodNames[food.id].orEmpty().associateBy { it.locale },
            imageVersion = imageVersions[food.id],
            composite = ingredients[food.id]?.let {
                CompositeDef(it, food.yield_amount, food.yield_unit_id, food.log_as_whole, food.ref_amount, food.ref_unit_id, manualNutrients)
            },
            createdAt = food.created_at,
            ownerName = ownerName,
        )
    }
    return Catalog(units, nutrients, Composites.derive(units, nutrients, foods))
}
