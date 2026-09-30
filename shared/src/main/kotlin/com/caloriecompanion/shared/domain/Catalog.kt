package com.caloriecompanion.shared.domain

import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.UnitKind

data class UnitDef(
    val id: Long,
    val name: String,
    val kind: UnitKind,
    val baseFactor: Double?,
    val archived: Boolean,
)

data class NutrientDef(
    val id: Long,
    val name: String,
    val measureUnit: String,
    val displayPrecision: Int,
    val sortOrder: Int,
    val parentId: Long?,
    val archived: Boolean,
)

data class FoodDef(
    val id: Long,
    val name: String,
    val refAmount: Double?,
    val refUnitId: Long?,
    val notes: String?,
    val archived: Boolean,
    /** Nutrient id -> amount per reference amount. */
    val nutrients: Map<Long, Double>,
    val links: List<FoodUnitLink>,
)

/** A user's complete catalog, loaded into memory for calculations. */
class Catalog(
    units: List<UnitDef>,
    nutrients: List<NutrientDef>,
    foods: List<FoodDef>,
) {
    val units: Map<Long, UnitDef> = units.associateBy { it.id }
    /** In display order. */
    val nutrients: List<NutrientDef> = nutrients.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val nutrientsById: Map<Long, NutrientDef> = this.nutrients.associateBy { it.id }
    val foods: Map<Long, FoodDef> = foods.associateBy { it.id }

    /** Nutrients shown in day views, totals and history (not archived). */
    val displayedNutrients: List<NutrientDef> get() = nutrients.filter { !it.archived }
}
