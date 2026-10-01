package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.db.Food
import com.caloriecompanion.db.Nutrient
import com.caloriecompanion.db.Quantity_unit
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

internal fun Quantity_unit.toDef(translations: Map<String, NameTranslation> = emptyMap()) =
    UnitDef(id, name, unitKindOf(kind), base_factor, archived, plural_suffix, translations)

internal fun Nutrient.toDef(translations: Map<String, NameTranslation> = emptyMap()) = NutrientDef(
    id = id,
    name = name,
    measureUnit = measure_unit,
    displayPrecision = display_precision.toInt(),
    sortOrder = sort_order.toInt(),
    parentId = parent_id,
    archived = archived,
    translations = translations,
)

/** [language]: the reader's language, for display names (L-5). */
internal fun UnitDef.toDto(language: String?) = UnitDto(
    id = id,
    name = name,
    kind = kind,
    baseFactor = baseFactor,
    archived = archived,
    pluralSuffix = pluralSuffix,
    translations = translations.inLanguageOrder(),
    displayName = displayName(language),
    displayPluralSuffix = displayPluralSuffix(language),
)

internal fun NutrientDef.toDto(language: String?) = NutrientDto(
    id = id,
    name = name,
    measureUnit = measureUnit,
    displayPrecision = displayPrecision,
    sortOrder = sortOrder,
    parentId = parentId,
    archived = archived,
    translations = translations.inLanguageOrder(),
    displayName = displayName(language),
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
)

internal fun unitTranslations(db: CalorieCompanionDatabase, userId: Long): Map<Long, Map<String, NameTranslation>> =
    db.quantityUnitQueries.selectUnitTranslations(userId).executeAsList()
        .groupBy({ it.unit_id }, { NameTranslation(it.locale, it.name, it.plural_suffix) })
        .mapValues { (_, list) -> list.associateBy { it.locale } }

internal fun nutrientTranslations(db: CalorieCompanionDatabase, userId: Long): Map<Long, Map<String, NameTranslation>> =
    db.nutrientQueries.selectNutrientTranslations(userId).executeAsList()
        .groupBy({ it.nutrient_id }, { NameTranslation(it.locale, it.name) })
        .mapValues { (_, list) -> list.associateBy { it.locale } }

internal fun com.caloriecompanion.db.Target.toDto() = TargetDto(id, nutrient_id, min_amount, max_amount, effective_from)

/** Loads a user's whole catalog. Catalogs are small (hundreds to a few thousand rows). */
fun loadCatalog(db: CalorieCompanionDatabase, userId: Long): Catalog {
    val unitNames = unitTranslations(db, userId)
    val units = db.quantityUnitQueries.selectUnits(userId).executeAsList().map { it.toDef(unitNames[it.id].orEmpty()) }
    val nutrientNames = nutrientTranslations(db, userId)
    val nutrients = db.nutrientQueries.selectNutrients(userId).executeAsList().map { it.toDef(nutrientNames[it.id].orEmpty()) }
    val foodNames = db.foodQueries.selectFoodTranslations(userId).executeAsList()
        .groupBy({ it.food_id }, { NameTranslation(it.locale, it.name) })
    val imageVersions = db.foodQueries.selectFoodImageVersions(userId).executeAsList().associate { it.food_id to it.updated_at }
    val ingredients = db.foodQueries.selectFoodIngredients(userId).executeAsList()
        .groupBy({ it.food_id }, { Ingredient(it.ingredient_id, it.unit_id, it.quantity) })
    val links = db.foodQueries.selectFoodUnits(userId).executeAsList().groupBy { it.food_id }
    val values = db.foodQueries.selectFoodNutrients(userId).executeAsList().groupBy { it.food_id }
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
        )
    }
    return Catalog(units, nutrients, Composites.derive(units, nutrients, foods))
}
