package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.service.NutrientService
import kotlin.test.Test
import kotlin.test.assertEquals

class NutrientGroupingTest {
    private val t = TestDb()
    private val nutrients = NutrientService(t.db, t.userId)

    private fun id(name: String) = nutrients.list(includeHidden = true).first { it.name == name }.id
    private fun names() = nutrients.list(includeHidden = true).map { it.name }

    private val seeded = listOf("Energy", "Protein", "Carbohydrates", "Sugars", "Fat", "Saturated fat", "Fiber", "Salt")

    @Test
    fun `moving a main nutrient moves its whole group`() {
        // The client sends Fat's group before Carbohydrates'.
        val ids = listOf("Energy", "Protein", "Fat", "Saturated fat", "Carbohydrates", "Sugars", "Fiber", "Salt").map(::id)
        nutrients.reorder(ids)
        assertEquals(listOf("Energy", "Protein", "Fat", "Saturated fat", "Carbohydrates", "Sugars", "Fiber", "Salt"), names())
    }

    @Test
    fun `a sub-nutrient can't be moved out of its group`() {
        // Sugars sent to the very top still ends up directly after Carbohydrates.
        nutrients.reorder(listOf("Sugars", "Energy", "Protein", "Carbohydrates", "Fat", "Saturated fat", "Fiber", "Salt").map(::id))
        assertEquals(seeded, names())
    }

    @Test
    fun `sub-nutrients are ordered among their siblings`() {
        val starch = nutrients.create(NutrientInput("Starch", "g", 1, id("Carbohydrates")))
        assertEquals(listOf("Energy", "Protein", "Carbohydrates", "Sugars", "Starch", "Fat"), names().take(6))
        val order = names().map(::id).toMutableList()
        order.remove(starch.id)
        order.add(order.indexOf(id("Sugars")), starch.id)
        nutrients.reorder(order)
        assertEquals(listOf("Carbohydrates", "Starch", "Sugars", "Fat"), names().drop(2).take(4))
    }

    @Test
    fun `giving a nutrient a parent moves it into the group`() {
        val salt = nutrients.get(id("Salt"))
        nutrients.update(salt.id, NutrientInput(salt.name, salt.measureUnit, salt.displayPrecision, parentId = id("Protein")))
        assertEquals(listOf("Energy", "Protein", "Salt", "Carbohydrates"), names().take(4))
        // Sort orders are stored in display order, so a plain sort by sortOrder agrees.
        assertEquals(names(), nutrients.list(includeHidden = true).sortedBy { it.sortOrder }.map { it.name })
    }
}
