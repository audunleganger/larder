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
    /** In display order: grouped, see [inDisplayOrder]. */
    val nutrients: List<NutrientDef> = nutrients.inDisplayOrder()
    val nutrientsById: Map<Long, NutrientDef> = this.nutrients.associateBy { it.id }
    val foods: Map<Long, FoodDef> = foods.associateBy { it.id }

    /** Nutrients shown in day views, totals and history (not archived). */
    val displayedNutrients: List<NutrientDef> get() = nutrients.filter { !it.archived }
}

/**
 * Display order of nutrients (N-3): main nutrients by sort order, each directly followed by its
 * sub-nutrients (one level) in their own sort order. A sub-nutrient can never drift away from its parent.
 */
fun List<NutrientDef>.inDisplayOrder(): List<NutrientDef> = groupedOrder(this, { it.id }, { it.parentId }, compareBy({ it.sortOrder }, { it.id }))

/** Orders [items] as groups: top-level items by [order], each followed by its children by [order]. */
fun <T> groupedOrder(items: List<T>, id: (T) -> Long, parentId: (T) -> Long?, order: Comparator<T>): List<T> {
    val sorted = items.sortedWith(order)
    val ids = sorted.mapTo(HashSet(), id)
    val isChild = { item: T -> parentId(item)?.let { it in ids } == true }
    val children = sorted.filter(isChild).groupBy { parentId(it) }
    return sorted.filterNot(isChild).flatMap { listOf(it) + children[id(it)].orEmpty() }
}
