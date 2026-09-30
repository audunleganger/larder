package com.caloriecompanion.shared.service

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.db.Food
import com.caloriecompanion.db.Nutrient
import com.caloriecompanion.db.Quantity_unit
import com.caloriecompanion.shared.api.FoodDto
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.TargetDto
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.Catalog
import com.caloriecompanion.shared.domain.FoodDef
import com.caloriecompanion.shared.domain.NutrientDef
import com.caloriecompanion.shared.domain.UnitDef

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

internal fun Quantity_unit.toDef() = UnitDef(id, name, unitKindOf(kind), base_factor, archived)

internal fun Nutrient.toDef() = NutrientDef(
    id = id,
    name = name,
    measureUnit = measure_unit,
    displayPrecision = display_precision.toInt(),
    sortOrder = sort_order.toInt(),
    parentId = parent_id,
    archived = archived,
)

internal fun UnitDef.toDto() = UnitDto(id, name, kind, baseFactor, archived)

internal fun NutrientDef.toDto() = NutrientDto(id, name, measureUnit, displayPrecision, sortOrder, parentId, archived)

internal fun FoodDef.toDto() = FoodDto(
    id = id,
    name = name,
    refAmount = refAmount,
    refUnitId = refUnitId,
    notes = notes,
    archived = archived,
    nutrients = nutrients.map { (nutrientId, amount) -> FoodNutrientValue(nutrientId, amount) },
    units = links,
)

internal fun com.caloriecompanion.db.Target.toDto() = TargetDto(id, nutrient_id, min_amount, max_amount, effective_from)

/** Loads a user's whole catalog. Catalogs are small (hundreds to a few thousand rows). */
fun loadCatalog(db: CalorieCompanionDatabase, userId: Long): Catalog {
    val units = db.quantityUnitQueries.selectUnits(userId).executeAsList().map { it.toDef() }
    val nutrients = db.nutrientQueries.selectNutrients(userId).executeAsList().map { it.toDef() }
    val links = db.foodQueries.selectFoodUnits(userId).executeAsList().groupBy { it.food_id }
    val values = db.foodQueries.selectFoodNutrients(userId).executeAsList().groupBy { it.food_id }
    val foods = db.foodQueries.selectFoods(userId).executeAsList().map { food: Food ->
        FoodDef(
            id = food.id,
            name = food.name,
            refAmount = food.ref_amount,
            refUnitId = food.ref_unit_id,
            notes = food.notes,
            archived = food.archived,
            nutrients = values[food.id].orEmpty().associate { it.nutrient_id to it.amount },
            links = links[food.id].orEmpty().map { FoodUnitLink(it.unit_id, it.equals_amount, it.equals_unit_id) },
        )
    }
    return Catalog(units, nutrients, foods)
}
