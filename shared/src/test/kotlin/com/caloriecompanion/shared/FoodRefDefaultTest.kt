package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodRefDefault
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.TransferService
import com.caloriecompanion.shared.service.UnitService
import kotlin.test.Test
import kotlin.test.assertEquals

class FoodRefDefaultTest {
    private val t = TestDb()
    private val units = UnitService(t.db, t.userId)
    private val foods = FoodService(t.db, t.userId)
    private fun unit(name: String) = units.list(includeHidden = true).first { it.name == name }.id

    @Test
    fun `starts as 100 g`() {
        assertEquals(FoodRefDefault(100.0, unit("g")), foods.refDefault())
    }

    @Test
    fun `remembers the reference of the last food set up`() {
        foods.create(FoodInput("Juice", 1.0, unit("dl")))
        assertEquals(FoodRefDefault(1.0, unit("dl")), foods.refDefault())
        // A food created without a reference doesn't change it.
        foods.create(FoodInput("Mystery"))
        assertEquals(FoodRefDefault(1.0, unit("dl")), foods.refDefault())
    }

    @Test
    fun `editing a food only counts when its reference changes`() {
        val old = foods.create(FoodInput("Old", 1.0, unit("piece")))
        foods.create(FoodInput("New", 2.0, unit("dl")))
        foods.update(old.id, FoodInput("Old", 1.0, unit("piece"), notes = "just a note"))
        assertEquals(FoodRefDefault(2.0, unit("dl")), foods.refDefault())
        foods.update(old.id, FoodInput("Old", 3.0, unit("piece")))
        assertEquals(FoodRefDefault(3.0, unit("piece")), foods.refDefault())
    }

    @Test
    fun `falls back to 100 g when the remembered unit is hidden`() {
        foods.create(FoodInput("Juice", 1.0, unit("dl")))
        units.setHidden(unit("dl"), true)
        assertEquals(FoodRefDefault(100.0, unit("g")), foods.refDefault())
    }

    @Test
    fun `imports don't change it`() {
        val transfer = TransferService(t.db, t.userId)
        val file = transfer.export()
        foods.create(FoodInput("Juice", 1.0, unit("dl")))
        transfer.import(file.copy(foods = file.foods + com.caloriecompanion.shared.api.ExportFood("Imported", 5.0, "kg")), ConflictStrategy.SKIP)
        assertEquals(FoodRefDefault(1.0, unit("dl")), foods.refDefault())
    }
}
