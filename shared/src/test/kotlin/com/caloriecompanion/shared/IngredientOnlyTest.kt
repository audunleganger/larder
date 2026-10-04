package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.CompositeInput
import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.Ingredient
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.TransferService
import com.caloriecompanion.shared.service.UnitService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Ingredient only" foods (F-15). */
class IngredientOnlyTest {
    private val t = TestDb()
    private val foods = FoodService(t.db, t.userId)
    private val entries = EntryService(t.db, t.userId)
    private val g by lazy { UnitService(t.db, t.userId).list(includeHidden = true).first { it.name == "g" }.id }
    private val energy by lazy { NutrientService(t.db, t.userId).list().first { it.name == "Energy" }.id }

    /** 900 kcal / 100 g. */
    private fun oil(ingredientOnly: Boolean? = true) =
        foods.create(FoodInput("Oil", 100.0, g, nutrients = listOf(FoodNutrientValue(energy, 900.0)), ingredientOnly = ingredientOnly)).id

    @Test
    fun `the flag is set on create and kept when an update leaves it out`() {
        val id = oil()
        assertTrue(foods.get(id).ingredientOnly)
        assertTrue(foods.list().first { it.id == id }.ingredientOnly)
        foods.update(id, FoodInput("Olive oil", 100.0, g))
        assertTrue(foods.get(id).ingredientOnly)
        foods.update(id, FoodInput("Olive oil", 100.0, g, ingredientOnly = false))
        assertFalse(foods.get(id).ingredientOnly)
        assertFalse(foods.get(oil(ingredientOnly = null)).ingredientOnly)
    }

    @Test
    fun `a composite with it logs an entry for it, and existing entries stay`() {
        val oil = oil(ingredientOnly = false)
        val before = entries.create(EntryInput(oil, g, 5.0, "2026-10-04", "12:00"))
        foods.update(oil, FoodInput("Oil", 100.0, g, nutrients = listOf(FoodNutrientValue(energy, 900.0)), ingredientOnly = true))
        val dressing = foods.create(FoodInput("Dressing", composite = CompositeInput(listOf(Ingredient(oil, g, 10.0))))).id

        val logged = entries.log(EntryInput(dressing, g, 10.0, "2026-10-04", "13:00"))
        assertEquals(listOf("Oil"), logged.map { it.foodName })
        assertEquals(listOf(oil, oil), entries.day("2026-10-04").entries.map { it.foodId })
        // 15 g of oil in all.
        assertEquals(135.0, entries.day("2026-10-04").totals.first { it.nutrientId == energy }.amount, 1e-9)
        // Editing the old entry keeps the food.
        assertEquals(oil, entries.update(before.id, EntryInput(oil, g, 6.0, "2026-10-04", "12:00")).foodId)
    }

    @Test
    fun `export and import keep the flag, and older files leave it alone`() {
        oil()
        val file = TransferService(t.db, t.userId).export()
        assertTrue(file.foods.first { it.name == "Oil" }.ingredientOnly)

        val other = TestDb()
        TransferService(other.db, other.userId).import(file, ConflictStrategy.SKIP)
        assertTrue(FoodService(other.db, other.userId).list().first { it.name == "Oil" }.ingredientOnly)

        val old = file.copy(version = 4, foods = file.foods.map { it.copy(ingredientOnly = false) })
        TransferService(t.db, t.userId).import(old, ConflictStrategy.OVERWRITE)
        assertTrue(foods.list().first { it.name == "Oil" }.ingredientOnly)
        TransferService(t.db, t.userId).import(old.copy(version = 5), ConflictStrategy.OVERWRITE)
        assertFalse(foods.list().first { it.name == "Oil" }.ingredientOnly)
    }
}
