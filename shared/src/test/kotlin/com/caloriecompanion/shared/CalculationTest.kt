package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.TargetStatus
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.Catalog
import com.caloriecompanion.shared.domain.FoodDef
import com.caloriecompanion.shared.domain.NutrientDef
import com.caloriecompanion.shared.domain.NutritionCalculator
import com.caloriecompanion.shared.domain.TargetRange
import com.caloriecompanion.shared.domain.UnitDef
import com.caloriecompanion.shared.domain.UnitResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculationTest {
    private val g = UnitDef(1, "g", UnitKind.MASS, 1.0, false)
    private val kg = UnitDef(2, "kg", UnitKind.MASS, 1000.0, false)
    private val ml = UnitDef(3, "ml", UnitKind.VOLUME, 1.0, false)
    private val dl = UnitDef(4, "dl", UnitKind.VOLUME, 100.0, false)
    private val slice = UnitDef(5, "slice", UnitKind.CUSTOM, null, false)
    private val loaf = UnitDef(6, "loaf", UnitKind.CUSTOM, null, false)
    private val piece = UnitDef(7, "piece", UnitKind.CUSTOM, null, false)
    private val oldMass = UnitDef(8, "stone", UnitKind.MASS, 6350.0, true)

    private val energy = NutrientDef(1, "Energy", "kcal", 0, 0, null, false)
    private val protein = NutrientDef(2, "Protein", "g", 1, 1, null, false)
    private val hidden = NutrientDef(3, "Hidden", "mg", 1, 2, null, true)

    private val bread = FoodDef(
        id = 1, name = "Bread", refAmount = 100.0, refUnitId = g.id, notes = null, archived = false,
        nutrients = mapOf(energy.id to 250.0, protein.id to 9.0, hidden.id to 1.0),
        links = listOf(FoodUnitLink(slice.id, 35.0, g.id), FoodUnitLink(loaf.id, 20.0, slice.id)),
    )
    private val egg = FoodDef(
        id = 2, name = "Egg", refAmount = 1.0, refUnitId = piece.id, notes = null, archived = false,
        nutrients = mapOf(energy.id to 70.0),
        links = listOf(FoodUnitLink(piece.id), FoodUnitLink(g.id, 1.0 / 60.0, piece.id)),
    )
    private val mystery = FoodDef(3, "Mystery", null, null, null, false, emptyMap(), listOf(FoodUnitLink(slice.id)))

    private val catalog = Catalog(
        listOf(g, kg, ml, dl, slice, loaf, piece, oldMass),
        listOf(energy, protein, hidden),
        listOf(bread, egg, mystery),
    )
    private val resolver = UnitResolver(catalog)
    private val calculator = NutritionCalculator(catalog)

    @Test
    fun `spec example - 2 slices of bread`() {
        val result = calculator.entryNutrition(bread, slice.id, 2.0)
        assertFalse(result.unresolved)
        assertEquals(175.0, result.amounts[energy.id]!!, 1e-9)
        assertEquals(6.3, result.amounts[protein.id]!!, 1e-9)
    }

    @Test
    fun `standard units convert automatically within a dimension`() {
        assertEquals(1000.0, resolver.amountInRefUnit(bread, kg.id)!!, 1e-9)
        assertEquals(2500.0, calculator.entryNutrition(bread, kg.id, 1.0).amounts[energy.id]!!, 1e-9)
    }

    @Test
    fun `custom units resolve through chains`() {
        // 1 loaf = 20 slices = 700 g
        assertEquals(700.0, resolver.amountInRefUnit(bread, loaf.id)!!, 1e-9)
    }

    @Test
    fun `links resolve in reverse direction`() {
        // Egg: reference 1 piece, 1 g = 1/60 piece -> 120 g = 2 pieces
        assertEquals(140.0, calculator.entryNutrition(egg, g.id, 120.0).amounts[energy.id]!!, 1e-9)
        // and kg via the standard-unit edge
        assertEquals(70.0 * 1000.0 / 60.0, calculator.entryNutrition(egg, kg.id, 1.0).amounts[energy.id]!!, 1e-6)
    }

    @Test
    fun `other dimensions are unresolvable`() {
        val result = calculator.entryNutrition(bread, dl.id, 1.0)
        assertTrue(result.unresolved)
        assertNull(result.amounts[energy.id])
    }

    @Test
    fun `food without reference amount is unresolved`() {
        assertTrue(calculator.entryNutrition(mystery, slice.id, 1.0).unresolved)
    }

    @Test
    fun `missing nutrient values are null but entry is resolved`() {
        val result = calculator.entryNutrition(egg, piece.id, 1.0)
        assertFalse(result.unresolved)
        assertNull(result.amounts[protein.id])
    }

    @Test
    fun `archived nutrients are not displayed`() {
        assertFalse(hidden.id in calculator.entryNutrition(bread, g.id, 1.0).amounts)
    }

    @Test
    fun `usable units include implicit standard units of the same dimension only`() {
        val usable = resolver.usableUnits(bread).associateBy { it.unitId }
        assertTrue(usable.getValue(g.id).explicit)
        assertTrue(usable.getValue(slice.id).explicit)
        assertFalse(usable.getValue(kg.id).explicit)
        assertFalse(dl.id in usable)
        assertFalse(oldMass.id in usable, "archived standard units aren't offered implicitly")
        assertEquals(35.0, usable.getValue(slice.id).amountInRefUnit!!, 1e-9)
    }

    @Test
    fun `totals count missing values and evaluate targets`() {
        val entries = listOf(
            calculator.entryNutrition(bread, slice.id, 2.0),
            calculator.entryNutrition(egg, piece.id, 1.0),
            calculator.entryNutrition(mystery, slice.id, 1.0),
        )
        val totals = calculator.totals(entries, mapOf(energy.id to TargetRange(200.0, 300.0), protein.id to TargetRange(50.0, null)))
            .associateBy { it.nutrientId }
        assertEquals(245.0, totals.getValue(energy.id).amount, 1e-9)
        assertEquals(1, totals.getValue(energy.id).missingCount)
        assertEquals(TargetStatus.WITHIN, totals.getValue(energy.id).status)
        assertEquals(2, totals.getValue(protein.id).missingCount)
        assertEquals(TargetStatus.BELOW, totals.getValue(protein.id).status)
    }

    @Test
    fun `target status boundaries`() {
        val range = TargetRange(100.0, 200.0)
        assertEquals(TargetStatus.BELOW, NutritionCalculator.targetStatus(99.9, range))
        assertEquals(TargetStatus.WITHIN, NutritionCalculator.targetStatus(100.0, range))
        assertEquals(TargetStatus.WITHIN, NutritionCalculator.targetStatus(200.0, range))
        assertEquals(TargetStatus.ABOVE, NutritionCalculator.targetStatus(200.1, range))
        assertEquals(TargetStatus.NONE, NutritionCalculator.targetStatus(5.0, null))
    }
}
