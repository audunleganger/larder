package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.CompositeInput
import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.Ingredient
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.TransferService
import com.caloriecompanion.shared.service.UnitService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompositeTest {
    private val t = TestDb()
    private val units = UnitService(t.db, t.userId)
    private val foods = FoodService(t.db, t.userId)
    private val entries = EntryService(t.db, t.userId)
    private fun unit(name: String) = units.list(includeArchived = true).first { it.name == name }.id
    private fun nutrient(name: String) = NutrientService(t.db, t.userId).list(includeArchived = true).first { it.name == name }.id
    private val energy get() = nutrient("Energy")
    private val slice by lazy { units.create(UnitInput("slice", UnitKind.CUSTOM)).id }

    private fun expectCode(code: String, block: () -> Unit) {
        assertEquals(code, assertFailsWith<AppException> { block() }.code)
    }

    /** 250 kcal / 100 g; 1 slice = 35 g. */
    private val bread by lazy {
        foods.create(FoodInput("Bread", 100.0, unit("g"), nutrients = listOf(FoodNutrientValue(energy, 250.0)), units = listOf(FoodUnitLink(slice, 35.0, unit("g"))))).id
    }

    /** 46 kcal / 100 ml. */
    private val milk by lazy { foods.create(FoodInput("Milk", 100.0, unit("ml"), nutrients = listOf(FoodNutrientValue(energy, 46.0)))).id }

    /** 717 kcal / 100 g. */
    private val butter by lazy { foods.create(FoodInput("Butter", 100.0, unit("g"), nutrients = listOf(FoodNutrientValue(energy, 717.0)))).id }

    /** 2 slices of bread + 200 ml milk, makes 1 serving: 175 + 92 = 267 kcal. */
    private fun breakfast(logAsWhole: Boolean = false) = foods.create(
        FoodInput(
            "Breakfast",
            composite = CompositeInput(
                listOf(Ingredient(bread, slice, 2.0), Ingredient(milk, unit("ml"), 200.0)),
                yieldAmount = 1.0, yieldUnitId = unit("serving"), logAsWhole = logAsWhole,
            ),
        ),
    ).id

    private fun energyOn(date: String) = entries.day(date).totals.first { it.nutrientId == energy }.amount

    @Test
    fun `nutrients are summed from the ingredients for the whole yield`() {
        val id = breakfast()
        val detail = foods.detail(id)
        assertEquals(267.0, detail.composite!!.nutrients.first { it.nutrientId == energy }.amount, 1e-9)
        assertEquals(1.0, detail.composite!!.yieldAmount)
        assertEquals(false, detail.composite!!.yieldAutomatic)
        // Milk is a volume, so there's no total weight.
        assertNull(detail.composite!!.totalGrams)
        assertTrue(foods.list().first { it.id == id }.composite)
        assertEquals(listOf("Breakfast"), foods.detail(bread).usedIn.map { it.name })
    }

    @Test
    fun `without a yield, a composite makes its total weight`() {
        val sandwich = foods.create(FoodInput("Sandwich", composite = CompositeInput(listOf(Ingredient(bread, slice, 2.0), Ingredient(butter, unit("g"), 10.0))))).id
        val detail = foods.detail(sandwich).composite!!
        assertEquals(80.0, detail.totalGrams!!, 1e-9)
        assertEquals(80.0, detail.yieldAmount!!, 1e-9)
        assertEquals(unit("g"), detail.yieldUnitId)
        assertTrue(detail.yieldAutomatic)
        // Logged as a whole: 40 g is half of it (175 + 71.7) / 2.
        foods.update(sandwich, FoodInput("Sandwich", composite = foods.get(sandwich).composite!!.copy(logAsWhole = true)))
        entries.create(EntryInput(sandwich, unit("g"), 40.0, "2026-10-01", "12:00"))
        assertEquals(123.35, energyOn("2026-10-01"), 1e-9)
    }

    @Test
    fun `logging a composite logs each ingredient as its own entry`() {
        val id = breakfast()
        val logged = entries.log(EntryInput(id, unit("serving"), 0.5, "2026-10-01", "08:00", note = "half"))
        assertEquals(listOf("Bread" to 1.0, "Milk" to 100.0), logged.map { it.foodName to it.quantity })
        assertTrue(logged.all { it.viaFoodId == id && it.viaFoodName == "Breakfast" && it.note == "half" && it.time == "08:00" })
        assertEquals(133.5, energyOn("2026-10-01"), 1e-9)
    }

    @Test
    fun `a composite set to be logged as a whole is one entry`() {
        val id = breakfast(logAsWhole = true)
        val logged = entries.log(EntryInput(id, unit("serving"), 1.0, "2026-10-01", "08:00"))
        assertEquals(listOf("Breakfast"), logged.map { it.foodName })
        assertEquals(267.0, energyOn("2026-10-01"), 1e-9)
    }

    @Test
    fun `nested composites are split all the way down`() {
        val id = breakfast()
        val brunch = foods.create(
            FoodInput("Brunch", composite = CompositeInput(listOf(Ingredient(id, unit("serving"), 2.0), Ingredient(butter, unit("g"), 10.0)), 1.0, unit("serving"))),
        ).id
        assertEquals(2 * 267.0 + 71.7, foods.detail(brunch).composite!!.nutrients.first { it.nutrientId == energy }.amount, 1e-9)
        val logged = entries.log(EntryInput(brunch, unit("serving"), 1.0, "2026-10-01", "11:00"))
        assertEquals(listOf("Bread" to 4.0, "Milk" to 400.0, "Butter" to 10.0), logged.map { it.foodName to it.quantity })
        assertTrue(logged.all { it.viaFoodId == brunch })
    }

    @Test
    fun `a food can't contain itself`() {
        val id = breakfast()
        val brunch = foods.create(FoodInput("Brunch", composite = CompositeInput(listOf(Ingredient(id, unit("serving"), 1.0))))).id
        expectCode(ErrorCodes.VALIDATION) {
            foods.update(id, FoodInput("Breakfast", composite = CompositeInput(listOf(Ingredient(brunch, unit("serving"), 1.0)))))
        }
        expectCode(ErrorCodes.VALIDATION) {
            foods.update(id, FoodInput("Breakfast", composite = CompositeInput(listOf(Ingredient(id, unit("serving"), 1.0)))))
        }
    }

    @Test
    fun `ingredients can't be deleted, used composites can`() {
        val id = breakfast()
        expectCode(ErrorCodes.REFERENCED) { foods.delete(bread) }
        expectCode(ErrorCodes.REFERENCED) { units.delete(slice) }
        // The split entries are bread and milk; the composite itself can go, and they stay.
        val logged = entries.log(EntryInput(id, unit("serving"), 1.0, "2026-10-01", "08:00"))
        foods.delete(id)
        assertEquals(logged.map { it.id }, entries.day("2026-10-01").entries.map { it.id })
        assertTrue(entries.day("2026-10-01").entries.all { it.viaFoodId == null })
    }

    @Test
    fun `an ingredient without a size is reported and contributes nothing`() {
        val glass = units.create(UnitInput("glass", UnitKind.CUSTOM)).id
        val id = foods.create(FoodInput("Snack", composite = CompositeInput(listOf(Ingredient(bread, slice, 1.0), Ingredient(milk, glass, 1.0)), 1.0, unit("serving")))).id
        val detail = foods.detail(id).composite!!
        assertEquals(listOf(false, true), detail.ingredients.map { it.unresolved })
        assertEquals(87.5, detail.nutrients.first { it.nutrientId == energy }.amount, 1e-9)
    }

    @Test
    fun `splitting needs to know how much the composite makes`() {
        // Milk in ml can't be weighed, so without a yield nothing can be split.
        val id = foods.create(FoodInput("Drink", composite = CompositeInput(listOf(Ingredient(milk, unit("ml"), 200.0))))).id
        expectCode(ErrorCodes.VALIDATION) { entries.log(EntryInput(id, unit("serving"), 1.0, "2026-10-01", "08:00")) }
        assertTrue(entries.day("2026-10-01").entries.isEmpty())
    }

    @Test
    fun `leaving the composite out of an update keeps it, an empty one removes it`() {
        val id = breakfast()
        foods.update(id, FoodInput("Breakfast", notes = "weekdays"))
        assertEquals(2, foods.get(id).composite!!.ingredients.size)
        foods.update(id, FoodInput("Breakfast", composite = CompositeInput(emptyList())))
        assertNull(foods.get(id).composite)
    }

    @Test
    fun `composites and where entries came from survive export and import`() {
        val id = breakfast()
        entries.log(EntryInput(id, unit("serving"), 1.0, "2026-10-01", "08:00"))
        val file = TransferService(t.db, t.userId).export()
        // Composites first, so the ingredients come later in the file.
        val reordered = file.copy(foods = file.foods.sortedByDescending { it.ingredients.size })

        val other = TestDb()
        TransferService(other.db, other.userId).import(reordered, ConflictStrategy.SKIP)
        val otherFoods = FoodService(other.db, other.userId)
        val imported = otherFoods.list().first { it.name == "Breakfast" }
        assertEquals(267.0, otherFoods.detail(imported.id).composite!!.nutrients.first { it.nutrientId == NutrientService(other.db, other.userId).list().first { it.name == "Energy" }.id }.amount, 1e-9)
        assertTrue(EntryService(other.db, other.userId).day("2026-10-01").entries.all { it.viaFoodName == "Breakfast" })
    }
}
