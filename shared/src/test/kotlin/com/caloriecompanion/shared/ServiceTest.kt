package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.api.TargetStatus
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.HistoryService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.TargetService
import com.caloriecompanion.shared.service.TransferService
import com.caloriecompanion.shared.service.UnitService
import com.caloriecompanion.shared.service.foldForSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceTest {
    private val t = TestDb()
    private val units = UnitService(t.db, t.userId)
    private val nutrients = NutrientService(t.db, t.userId)
    private val foods = FoodService(t.db, t.userId)
    private val entries = EntryService(t.db, t.userId)
    private val targets = TargetService(t.db, t.userId)

    private fun unit(name: String) = units.list(includeArchived = true).first { it.name == name }.id
    private fun nutrient(name: String) = nutrients.list(includeArchived = true).first { it.name == name }.id

    private fun bread() = foods.create(
        FoodInput(
            name = "Bread",
            refAmount = 100.0,
            refUnitId = unit("g"),
            nutrients = listOf(FoodNutrientValue(nutrient("Energy"), 250.0), FoodNutrientValue(nutrient("Protein"), 9.0)),
            units = listOf(FoodUnitLink(units.create(UnitInput("slice", UnitKind.CUSTOM)).id, 35.0, unit("g"))),
        ),
    )

    private fun expectCode(code: String, block: () -> Unit) {
        val e = assertFailsWith<AppException> { block() }
        assertEquals(code, e.code)
    }

    @Test
    fun `new user is seeded with units and nutrients`() {
        assertTrue(units.list().any { it.name == "kg" && it.baseFactor == 1000.0 })
        assertEquals(listOf("Energy", "Protein", "Carbohydrates", "Sugars", "Fat", "Saturated fat", "Fiber", "Salt"), nutrients.list().map { it.name })
        assertEquals(nutrient("Carbohydrates"), nutrients.get(nutrient("Sugars")).parentId)
    }

    @Test
    fun `names are unique case-insensitively and trimmed`() {
        foods.create(FoodInput("Milk"))
        expectCode(ErrorCodes.NAME_TAKEN) { foods.create(FoodInput("  milk ")) }
        expectCode(ErrorCodes.NAME_TAKEN) { units.create(UnitInput("G", UnitKind.MASS, 1.0)) }
        expectCode(ErrorCodes.NAME_REQUIRED) { foods.create(FoodInput("   ")) }
    }

    @Test
    fun `food can be renamed and keeps its links`() {
        val food = bread()
        entries.create(EntryInput(food.id, unit("slice"), 2.0, "2026-09-30", "08:00"))
        foods.update(food.id, FoodInput("Rye bread", 100.0, unit("g"), units = food.units, nutrients = food.nutrients))
        assertEquals("Rye bread", entries.day("2026-09-30").entries.single().foodName)
    }

    @Test
    fun `day view calculates entries and totals`() {
        val food = bread()
        entries.create(EntryInput(food.id, unit("slice"), 2.0, "2026-09-30", "08:00"))
        entries.create(EntryInput(food.id, unit("kg"), 0.1, "2026-09-30", "07:00"))
        val day = entries.day("2026-09-30")
        assertEquals(listOf("07:00", "08:00"), day.entries.map { it.time })
        val energy = day.totals.first { it.nutrientId == nutrient("Energy") }
        assertEquals(425.0, energy.amount, 1e-9)
        assertEquals(0, energy.missingCount)
        val fat = day.totals.first { it.nutrientId == nutrient("Fat") }
        assertEquals(2, fat.missingCount)
    }

    @Test
    fun `editing a food recalculates past entries`() {
        val food = bread()
        entries.create(EntryInput(food.id, unit("g"), 100.0, "2026-09-01", "12:00"))
        foods.update(food.id, FoodInput("Bread", 100.0, unit("g"), nutrients = listOf(FoodNutrientValue(nutrient("Energy"), 300.0))))
        assertEquals(300.0, entries.day("2026-09-01").totals.first { it.nutrientId == nutrient("Energy") }.amount, 1e-9)
    }

    @Test
    fun `logging with an unlinked unit links it and flags the entry`() {
        val food = foods.create(FoodInput("Soup"))
        val entry = entries.create(EntryInput(food.id, unit("serving"), 1.0, "2026-09-30", "12:00"))
        assertTrue(entry.unresolved)
        assertTrue(entry.nutrients.all { it.amount == null })
        assertEquals(listOf(unit("serving")), foods.get(food.id).units.map { it.unitId })
    }

    @Test
    fun `referenced items cannot be deleted but can be archived`() {
        val food = bread()
        entries.create(EntryInput(food.id, unit("slice"), 1.0, "2026-09-30", "12:00"))
        expectCode(ErrorCodes.REFERENCED) { foods.delete(food.id) }
        expectCode(ErrorCodes.REFERENCED) { units.delete(unit("slice")) }
        expectCode(ErrorCodes.REFERENCED) { nutrients.delete(nutrient("Energy")) }
        foods.setArchived(food.id, true)
        assertTrue(foods.list().none { it.id == food.id })
        assertTrue(foods.list(includeArchived = true).any { it.id == food.id })
        // unreferenced items can be deleted
        units.delete(unit("lb"))
        assertTrue(units.list().none { it.name == "lb" })
    }

    @Test
    fun `detail views link foods, units, nutrients and entries`() {
        val food = bread()
        entries.create(EntryInput(food.id, unit("slice"), 2.0, "2026-09-30", "08:00"))
        entries.create(EntryInput(food.id, unit("g"), 50.0, "2026-09-29", "08:00"))

        val foodDetail = foods.detail(food.id)
        assertEquals(listOf("2026-09-30", "2026-09-29"), foodDetail.entries.map { it.date })
        assertEquals("slice", foodDetail.entries.first().unitName)
        assertTrue(foodDetail.usableUnits.any { it.name == "kg" && !it.explicit })

        val slice = units.detail(unit("slice"))
        assertEquals(listOf("Bread"), slice.foods.map { it.name })
        assertEquals(listOf("2026-09-30"), slice.dates)
        val kg = units.detail(unit("kg"))
        assertEquals(listOf("Bread"), kg.implicitFoods.map { it.name })

        val protein = nutrients.detail(nutrient("Protein"))
        assertEquals(listOf("Bread"), protein.foods.map { it.foodName })
        assertEquals(6.3, protein.entries.first().amount!!, 1e-9)
    }

    @Test
    fun `nutrients can be reordered`() {
        val ids = nutrients.list().map { it.id }
        nutrients.reorder(listOf(ids.last(), ids.first()))
        val order = nutrients.list().map { it.id }
        assertEquals(ids.last(), order[0])
        assertEquals(ids.first(), order[1])
        assertEquals(ids.size, order.size)
    }

    @Test
    fun `nutrient parent is limited to one level`() {
        expectCode(ErrorCodes.VALIDATION) {
            nutrients.create(NutrientInput("Fructose", "g", 1, parentId = nutrient("Sugars")))
        }
    }

    @Test
    fun `targets are versioned by effective date`() {
        val food = bread()
        val energy = nutrient("Energy")
        targets.set(TargetInput(energy, min = 100.0, max = 200.0, effectiveFrom = "2026-01-01"))
        targets.set(TargetInput(energy, min = 1000.0, max = 2000.0, effectiveFrom = "2026-09-15"))
        entries.create(EntryInput(food.id, unit("g"), 60.0, "2026-09-01", "12:00"))
        entries.create(EntryInput(food.id, unit("g"), 60.0, "2026-09-20", "12:00"))
        assertEquals(TargetStatus.WITHIN, entries.day("2026-09-01").totals.first { it.nutrientId == energy }.status)
        assertEquals(TargetStatus.BELOW, entries.day("2026-09-20").totals.first { it.nutrientId == energy }.status)
        assertEquals(TargetStatus.NONE, entries.day("2025-12-31").totals.first { it.nutrientId == energy }.status)
    }

    @Test
    fun `history treats empty days as gaps`() {
        val food = bread()
        entries.create(EntryInput(food.id, unit("g"), 100.0, "2026-09-01", "12:00"))
        entries.create(EntryInput(food.id, unit("g"), 200.0, "2026-09-03", "12:00"))
        val history = HistoryService(t.db, t.userId).history("2026-09-01", "2026-09-04")
        assertEquals(4, history.days.size)
        assertEquals(0, history.days[1].entryCount)
        val energy = history.summary.first { it.nutrientId == nutrient("Energy") }
        assertEquals(2, energy.loggedDays)
        assertEquals(375.0, energy.average!!, 1e-9)
    }

    @Test
    fun `export and import round-trip into a fresh catalog`() {
        val food = bread()
        entries.create(EntryInput(food.id, unit("slice"), 2.0, "2026-09-30", "08:00", note = "toast"))
        targets.set(TargetInput(nutrient("Energy"), 1800.0, 2200.0, "2026-09-01"))
        val export = TransferService(t.db, t.userId).export()

        val other = TestDb()
        val result = TransferService(other.db, other.userId).import(export, ConflictStrategy.SKIP)
        assertEquals(1, result.foods.created)
        assertEquals(1, result.entries.created)
        assertEquals(1, result.units.created, "only the custom 'slice' unit is new")
        val day = EntryService(other.db, other.userId).day("2026-09-30")
        assertEquals(175.0, day.totals.first().amount, 1e-9)
        assertEquals("toast", day.entries.single().note)

        // Importing again is harmless
        val again = TransferService(other.db, other.userId).import(export, ConflictStrategy.SKIP)
        assertEquals(1, again.entries.skipped)
        assertEquals(0, again.entries.created)
    }

    @Test
    fun `invalid imports roll back completely`() {
        val export = TransferService(t.db, t.userId).export()
        val broken = export.copy(
            foods = listOf(com.caloriecompanion.shared.api.ExportFood("New food", refAmount = 1.0, refUnit = "no-such-unit")),
        )
        expectCode(ErrorCodes.INVALID_IMPORT) { TransferService(t.db, t.userId).import(broken, ConflictStrategy.OVERWRITE) }
        assertTrue(foods.list().isEmpty())
    }

    @Test
    fun `search ignores case and diacritics`() {
        foods.create(FoodInput("Blåbær"))
        foods.create(FoodInput("Brød"))
        foods.create(FoodInput("Rugbrød"))
        assertEquals(listOf("Blåbær"), foods.list("blabaer").map { it.name })
        assertEquals(listOf("Brød", "Rugbrød"), foods.list("brod").map { it.name })
        assertEquals("cafe", foldForSearch("Café"))
    }

    @Test
    fun `validation errors`() {
        val food = bread()
        expectCode(ErrorCodes.VALIDATION) { entries.create(EntryInput(food.id, unit("g"), 0.0, "2026-09-30", "08:00")) }
        expectCode(ErrorCodes.VALIDATION) { entries.create(EntryInput(food.id, unit("g"), 1.0, "2026-13-01", "08:00")) }
        expectCode(ErrorCodes.VALIDATION) { entries.create(EntryInput(food.id, unit("g"), 1.0, "2026-09-30", "25:00")) }
        expectCode(ErrorCodes.VALIDATION) { units.create(UnitInput("stone", UnitKind.MASS, null)) }
        expectCode(ErrorCodes.VALIDATION) { foods.create(FoodInput("X", refAmount = 100.0)) }
        expectCode(ErrorCodes.VALIDATION) { targets.set(TargetInput(nutrient("Energy"), 10.0, 5.0, "2026-09-30")) }
        expectCode(ErrorCodes.NOT_FOUND) { foods.detail(9999) }
        assertNull(entries.preview(com.caloriecompanion.shared.api.PreviewInput(food.id, unit("dl"), 1.0)).nutrients.first().amount)
        assertFalse(entries.preview(com.caloriecompanion.shared.api.PreviewInput(food.id, unit("slice"), 1.0)).unresolved)
    }
}
