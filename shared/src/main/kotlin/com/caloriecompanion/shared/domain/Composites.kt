package com.caloriecompanion.shared.domain

import com.caloriecompanion.shared.api.UnitKind
import kotlin.math.abs

/**
 * Composite foods (F-10): a composite food's nutrients are the sum of its ingredients' (each
 * calculated like an entry), for the whole amount the ingredients make — its yield. The yield is
 * as set by the user, or else the ingredients' total weight. Ingredients may be composite themselves.
 */
object Composites {
    /** Nesting deeper than this is treated as a loop. */
    const val MAX_DEPTH = 20

    /** The unit total weights are given in: the mass unit closest to 1 g. */
    fun gramUnit(units: Collection<UnitDef>): UnitDef? =
        units.filter { it.kind == UnitKind.MASS && it.baseFactor != null && it.baseFactor > 0 }.minByOrNull { abs(it.baseFactor!! - 1.0) }

    /**
     * Returns [foods] with every composite food's reference amount (its yield), reference unit and
     * nutrients derived from its ingredients. Foods in a loop (which validation prevents) get none.
     */
    fun derive(units: List<UnitDef>, nutrients: List<NutrientDef>, foods: List<FoodDef>): List<FoodDef> {
        if (foods.none { it.composite != null }) return foods
        val base = Catalog(units, nutrients, emptyList())
        val resolver = UnitResolver(base)
        val calculator = NutritionCalculator(base)
        val gram = gramUnit(units)
        val raw = foods.associateBy { it.id }
        val done = HashMap<Long, FoodDef?>()
        val visiting = HashSet<Long>()

        fun resolve(id: Long, depth: Int): FoodDef? {
            if (id in done) return done[id]
            val food = raw[id] ?: return null
            val composite = food.composite ?: return food.also { done[id] = it }
            if (depth > MAX_DEPTH || !visiting.add(id)) return null
            val sums = HashMap<Long, Double>()
            val unresolved = HashSet<Int>()
            val grams = composite.ingredients.mapIndexed { index, ingredient ->
                val part = resolve(ingredient.foodId, depth + 1)
                val factor = part?.let { calculator.factor(it, ingredient.unitId, ingredient.quantity) }
                if (part == null || factor == null) {
                    unresolved += index
                } else {
                    part.nutrients.forEach { (nutrientId, value) -> sums[nutrientId] = (sums[nutrientId] ?: 0.0) + factor * value }
                }
                if (part != null && gram != null) resolver.convert(part, ingredient.unitId, gram.id)?.times(ingredient.quantity) else null
            }
            visiting.remove(id)
            val totalGrams = if (grams.isNotEmpty() && grams.all { it != null }) grams.sumOf { it!! } else null
            val (yieldAmount, yieldUnit) = when {
                composite.yieldAmount != null -> composite.yieldAmount to composite.yieldUnitId
                totalGrams != null && totalGrams > 0 -> totalGrams to gram?.id
                else -> null to null
            }
            return food.copy(
                refAmount = yieldAmount,
                refUnitId = yieldUnit,
                nutrients = sums,
                composite = composite.copy(totalGrams = totalGrams, unresolved = unresolved, grams = grams),
            ).also { done[id] = it }
        }

        return foods.map { food ->
            resolve(food.id, 0) ?: food.copy(refAmount = null, refUnitId = null, nutrients = emptyMap())
        }
    }

    /** True if [ingredientId], or anything inside it, is [foodId] — adding it to [foodId] would make a loop. */
    fun contains(foods: Map<Long, FoodDef>, ingredientId: Long, foodId: Long, depth: Int = 0): Boolean {
        if (ingredientId == foodId) return true
        if (depth > MAX_DEPTH) return true
        return foods[ingredientId]?.composite?.ingredients.orEmpty().any { contains(foods, it.foodId, foodId, depth + 1) }
    }
}
