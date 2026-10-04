package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.CompositeInput
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.Ingredient
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.UnitService
import kotlin.test.Test
import kotlin.test.assertEquals

/** The food list's nutrient values (F-17). */
class FoodListTest {
    private val t = TestDb()
    private val foods = FoodService(t.db, t.userId)
    private val g by lazy { UnitService(t.db, t.userId).list(includeHidden = true).first { it.name == "g" }.id }
    private val nutrients by lazy { NutrientService(t.db, t.userId).list().associate { it.name to it.id } }

    @Test
    fun `the list has each food's values per reference amount, calculated for composites`() {
        val energy = nutrients.getValue("Energy")
        val fat = nutrients.getValue("Fat")
        val oil = foods.create(FoodInput("Oil", 100.0, g, nutrients = listOf(FoodNutrientValue(energy, 900.0), FoodNutrientValue(fat, 100.0)))).id
        val vinegar = foods.create(FoodInput("Vinegar", 100.0, g, nutrients = listOf(FoodNutrientValue(energy, 20.0)))).id
        val dressing = foods.create(FoodInput("Dressing", composite = CompositeInput(listOf(Ingredient(oil, g, 30.0), Ingredient(vinegar, g, 10.0))))).id
        val bare = foods.create(FoodInput("Mystery")).id

        val list = foods.list().associateBy { it.id }
        assertEquals(mapOf(energy to 900.0, fat to 100.0), list.getValue(oil).nutrients)
        // The dressing makes 40 g: 270 + 2 kcal, and 30 g of fat.
        assertEquals(40.0, list.getValue(dressing).refAmount)
        assertEquals(272.0, list.getValue(dressing).nutrients.getValue(energy), 1e-9)
        assertEquals(30.0, list.getValue(dressing).nutrients.getValue(fat), 1e-9)
        assertEquals(emptyMap(), list.getValue(bare).nutrients)
    }
}
