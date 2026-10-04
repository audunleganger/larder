package com.caloriecompanion.shared.domain

import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.api.UsableUnit

/**
 * Converts between units for a given food (U-4, U-5, C-2).
 *
 * Units form a graph: standard units of the same dimension convert via their global base factor,
 * and each food-unit link "1 A = x B" is an edge in both directions. Conversion is a search from
 * the entry's unit to the food's reference unit, multiplying factors along the way.
 */
class UnitResolver(private val catalog: Catalog) {

    /** How many of [toUnitId] one [fromUnitId] is, for [food]; null if there's no conversion path. */
    fun convert(food: FoodDef, fromUnitId: Long, toUnitId: Long): Double? {
        if (fromUnitId == toUnitId) return 1.0
        val edges = edges(food)
        val factors = HashMap<Long, Double>()
        factors[fromUnitId] = 1.0
        val queue = ArrayDeque<Long>()
        queue.add(fromUnitId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentFactor = factors.getValue(current)
            for ((next, factor) in edges[current].orEmpty()) {
                if (next in factors) continue
                factors[next] = currentFactor * factor
                if (next == toUnitId) return factors[next]
                queue.add(next)
            }
        }
        return null
    }

    /** How much of the food's reference unit one [unitId] is; null if unresolvable or no reference unit. */
    fun amountInRefUnit(food: FoodDef, unitId: Long): Double? {
        val refUnitId = food.refUnitId ?: return null
        return convert(food, unitId, refUnitId)
    }

    /**
     * Units usable for [food] (E-1): explicitly linked units and the reference unit, plus all standard
     * units in any dimension the food already has a standard unit in (U-4). Units the reader has hidden
     * are only included when explicitly linked.
     */
    fun usableUnits(food: FoodDef, language: String? = null): List<UsableUnit> {
        val explicitIds = LinkedHashSet<Long>()
        food.refUnitId?.let(explicitIds::add)
        food.links.forEach { explicitIds.add(it.unitId) }

        val dimensions = HashSet<UnitKind>()
        val involved = explicitIds + food.links.mapNotNull { it.equalsUnitId }
        for (id in involved) {
            val unit = catalog.units[id] ?: continue
            if (unit.kind.isStandard) dimensions.add(unit.kind)
        }
        val implicitIds = catalog.units.values
            .filter { it.kind in dimensions && !it.hidden && it.baseFactor != null && it.id !in explicitIds }
            .map { it.id }

        return (explicitIds.map { it to true } + implicitIds.map { it to false })
            .mapNotNull { (id, explicit) -> catalog.units[id]?.let { it to explicit } }
            // The food's own units first, each group in the reader's order (U-10).
            .sortedWith(compareBy<Pair<UnitDef, Boolean>> { !it.second }.thenBy(unitOrder(language)) { it.first })
            .map { (unit, explicit) ->
                val id = unit.id
                UsableUnit(
                    unitId = id,
                    name = unit.displayName(language),
                    kind = unit.kind,
                    explicit = explicit,
                    amountInRefUnit = amountInRefUnit(food, id),
                    pluralSuffix = unit.displayPluralSuffix(language),
                )
            }
    }

    fun isUsable(food: FoodDef, unitId: Long): Boolean = usableUnits(food).any { it.unitId == unitId }

    private fun edges(food: FoodDef): Map<Long, List<Pair<Long, Double>>> {
        val edges = HashMap<Long, MutableList<Pair<Long, Double>>>()
        fun add(from: Long, to: Long, factor: Double) {
            if (!factor.isFinite() || factor <= 0.0) return
            edges.getOrPut(from) { mutableListOf() }.add(to to factor)
        }
        // Standard units within one dimension: 1 kg = 1000/1 g.
        val standard = catalog.units.values.filter { it.kind.isStandard && it.baseFactor != null && it.baseFactor > 0 }
        for (a in standard) for (b in standard) {
            if (a.id != b.id && a.kind == b.kind) add(a.id, b.id, a.baseFactor!! / b.baseFactor!!)
        }
        // Food-specific links: 1 slice = 35 g.
        for (link in food.links) {
            val amount = link.equalsAmount ?: continue
            val other = link.equalsUnitId ?: continue
            if (amount <= 0.0 || other == link.unitId) continue
            add(link.unitId, other, amount)
            add(other, link.unitId, 1.0 / amount)
        }
        return edges
    }
}
