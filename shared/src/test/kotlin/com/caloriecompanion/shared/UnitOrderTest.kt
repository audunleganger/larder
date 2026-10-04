package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.Seeder
import com.caloriecompanion.shared.service.UnitService
import java.text.Collator
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Each user's own order of the units they show (U-10). */
class UnitOrderTest {
    private val t = TestDb()
    private val units = UnitService(t.db, t.userId)
    private fun names(service: UnitService = units) = service.list().map { it.displayName }
    private fun id(name: String) = units.list(includeHidden = true).first { it.name == name }.id
    private fun alphabetical(names: List<String>, language: String = "en") = names.sortedWith(Collator.getInstance(Locale.forLanguageTag(language)))

    @Test
    fun `units start in alphabetical order, by their name in the reader's language`() {
        assertEquals(alphabetical(names()), names())
        assertTrue(units.list().all { it.sortOrder == null })
        val norwegian = names(UnitService(t.db, t.userId, "nb"))
        assertEquals(alphabetical(norwegian, "nb"), norwegian)
        // A new unit falls into its alphabetical place.
        units.create(UnitInput("bowl", UnitKind.CUSTOM))
        assertEquals(alphabetical(names()), names())
    }

    @Test
    fun `a reordered list keeps the rest in place, and new or shown again units go last`() {
        val before = names()
        units.reorder(listOf(id("serving")))
        assertEquals(listOf("serving") + before.filter { it != "serving" }, names())

        units.create(UnitInput("bowl", UnitKind.CUSTOM))
        assertEquals("bowl", names().last())
        units.setHidden(id("g"), true)
        units.setHidden(id("g"), false)
        assertEquals("g", names().last())
        // Hidden units are ignored.
        units.setHidden(id("kg"), true)
        units.reorder(listOf(id("kg"), id("bowl")))
        assertEquals("bowl", names().first())
    }

    @Test
    fun `reset puts the units back in alphabetical order`() {
        units.reorder(listOf(id("serving"), id("g")))
        units.resetOrder()
        assertEquals(alphabetical(names()), names())
        assertTrue(units.list().all { it.sortOrder == null })
    }

    @Test
    fun `each user has their own order`() {
        t.db.appUserQueries.insertUser("kari", "x", false, "en", 0)
        val kari = t.db.appUserQueries.lastInsertRowId().executeAsOne()
        Seeder.seed(t.db, kari)
        units.reorder(listOf(id("serving")))
        val theirs = names(UnitService(t.db, kari))
        assertEquals(alphabetical(theirs), theirs)
    }

    @Test
    fun `a food's own units come first, each group in the user's order`() {
        val g = id("g")
        val slice = units.create(UnitInput("slice", UnitKind.CUSTOM)).id
        val foods = FoodService(t.db, t.userId)
        val bread = foods.create(FoodInput("Bread", 100.0, g, units = listOf(FoodUnitLink(slice, 35.0, g)))).id
        val usable = { foods.detail(bread).usableUnits.map { it.name } }
        assertEquals(listOf("g", "slice"), usable().take(2))
        assertEquals(alphabetical(usable().drop(2)), usable().drop(2))

        units.reorder(listOf(slice, id("mg"), id("kg")))
        assertEquals(listOf("slice", "g", "mg", "kg"), usable().take(4))
    }
}
