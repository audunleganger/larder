package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.ExportNutrient
import com.caloriecompanion.shared.api.ExportUnit
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.NameTranslation
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.Seeder
import com.caloriecompanion.shared.service.TransferService
import com.caloriecompanion.shared.service.UnitService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Units and nutrients are shared by a server's users, each showing the ones they choose. */
class SharingTest {
    private val t = TestDb()
    private val admin = t.userId

    /** A second, non-admin user, set up like the server does. */
    private fun user(name: String): Long {
        t.db.appUserQueries.insertUser(name, "x", false, "en", 0)
        val id = t.db.appUserQueries.lastInsertRowId().executeAsOne()
        Seeder.seed(t.db, id)
        return id
    }

    private val kari = user("kari")
    private val ola = user("ola")

    private fun units(user: Long) = UnitService(t.db, user)
    private fun nutrients(user: Long) = NutrientService(t.db, user)
    private fun unitId(name: String) = units(admin).list(includeHidden = true).first { it.name == name }.id
    private fun nutrientId(name: String) = nutrients(admin).list(includeHidden = true).first { it.name == name }.id

    private fun expectCode(code: String, block: () -> Unit): AppException =
        assertFailsWith<AppException> { block() }.also { assertEquals(code, it.code) }

    @Test
    fun `every user starts with the built-in units and nutrients, made once by the first user`() {
        val kg = units(kari).list().first { it.name == "kg" }
        assertTrue(kg.builtIn)
        assertEquals("local", kg.createdBy)
        assertEquals(units(admin).list().map { it.id }, units(kari).list().map { it.id })
        assertEquals(1, units(admin).list(includeHidden = true).count { it.name == "kg" })
        assertEquals(nutrients(admin).list().map { it.id }, nutrients(kari).list().map { it.id })
    }

    @Test
    fun `only admins can change built-ins, and only the maker or admins other units`() {
        assertFalse(units(kari).get(unitId("g")).canEdit)
        expectCode(ErrorCodes.FORBIDDEN) { units(kari).update(unitId("g"), UnitInput("gram", UnitKind.MASS, 1.0)) }
        expectCode(ErrorCodes.FORBIDDEN) { nutrients(kari).update(nutrientId("Salt"), NutrientInput("Sodium", "g")) }
        assertTrue(units(admin).get(unitId("g")).canEdit)

        val slice = units(kari).create(UnitInput("slice", UnitKind.CUSTOM))
        assertTrue(slice.canEdit)
        assertEquals("kari", slice.createdBy)
        expectCode(ErrorCodes.FORBIDDEN) { units(ola).update(slice.id, UnitInput("slab", UnitKind.CUSTOM)) }
        expectCode(ErrorCodes.FORBIDDEN) { units(ola).delete(slice.id) }
        units(admin).update(slice.id, UnitInput("slab", UnitKind.CUSTOM))
        units(kari).update(slice.id, UnitInput("slice", UnitKind.CUSTOM))
    }

    @Test
    fun `a new unit is shown for its maker and hidden for everyone else`() {
        val slice = units(kari).create(UnitInput("slice", UnitKind.CUSTOM))
        assertTrue(units(kari).list().any { it.id == slice.id })
        assertFalse(units(ola).list().any { it.id == slice.id })
        assertTrue(units(ola).list(includeHidden = true).first { it.id == slice.id }.hidden)
        units(ola).setHidden(slice.id, false)
        assertTrue(units(ola).list().any { it.id == slice.id })
    }

    @Test
    fun `names are unique across the server, in any language, and a clash names the existing one`() {
        val slice = units(kari).create(UnitInput("slice", UnitKind.CUSTOM))
        val e = expectCode(ErrorCodes.NAME_TAKEN) { units(ola).create(UnitInput("Slice", UnitKind.CUSTOM)) }
        assertEquals(slice.id, e.details["id"])
        val taken = expectCode(ErrorCodes.NAME_TAKEN) { units(ola).create(UnitInput("porsjon", UnitKind.CUSTOM)) }
        assertEquals(unitId("serving"), taken.details["id"])
        expectCode(ErrorCodes.NAME_TAKEN) { nutrients(ola).create(NutrientInput("fett", "g")) }
    }

    @Test
    fun `anyone can hide a unit, and it keeps working for foods that use it`() {
        val foods = FoodService(t.db, kari)
        val slice = units(kari).create(UnitInput("slice", UnitKind.CUSTOM))
        val bread = foods.create(
            FoodInput("Bread", 100.0, unitId("g"), nutrients = listOf(FoodNutrientValue(nutrientId("Energy"), 250.0)), units = listOf(FoodUnitLink(slice.id, 35.0, unitId("g")))),
        )
        units(kari).setHidden(slice.id, true)
        units(kari).setHidden(unitId("oz"), true)

        val usable = foods.detail(bread.id).usableUnits.map { it.unitId }
        assertTrue(slice.id in usable, "linked units are offered even when hidden")
        assertFalse(unitId("oz") in usable, "hidden standard units aren't offered automatically")
        val entry = EntryService(t.db, kari).create(EntryInput(bread.id, slice.id, 2.0, "2026-10-04", "08:00"))
        assertEquals("slice", entry.unitName)
        // A hidden standard unit still converts: 1 oz of 250 kcal / 100 g.
        val preview = EntryService(t.db, kari).preview(PreviewInput(bread.id, unitId("oz"), 1.0))
        assertEquals(250 * 28.349523125 / 100, preview.nutrients.first { it.nutrientId == nutrientId("Energy") }.amount!!, 1e-9)
    }

    @Test
    fun `a unit or nutrient another user's data uses can't be deleted`() {
        val slice = units(kari).create(UnitInput("slice", UnitKind.CUSTOM))
        val vitaminC = nutrients(kari).create(NutrientInput("Vitamin C", "mg"))
        FoodService(t.db, ola).create(
            FoodInput("Orange", 100.0, unitId("g"), nutrients = listOf(FoodNutrientValue(vitaminC.id, 50.0)), units = listOf(FoodUnitLink(slice.id))),
        )
        expectCode(ErrorCodes.REFERENCED) { units(kari).delete(slice.id) }
        expectCode(ErrorCodes.REFERENCED) { nutrients(kari).delete(vitaminC.id) }
        val unused = units(kari).create(UnitInput("wedge", UnitKind.CUSTOM))
        units(kari).delete(unused.id)
    }

    @Test
    fun `each user has their own nutrient order, and hidden nutrients leave their totals`() {
        val energy = nutrientId("Energy")
        val protein = nutrientId("Protein")
        nutrients(kari).reorder(listOf(protein, energy))
        assertEquals(listOf(protein, energy), nutrients(kari).list().take(2).map { it.id })
        assertEquals(listOf(energy, protein), nutrients(ola).list().take(2).map { it.id })

        nutrients(ola).setHidden(protein, true)
        assertFalse(EntryService(t.db, ola).day("2026-10-04").totals.any { it.nutrientId == protein })
        assertTrue(EntryService(t.db, kari).day("2026-10-04").totals.any { it.nutrientId == protein })
        // Showing it again puts it last.
        nutrients(ola).setHidden(protein, false)
        assertEquals(protein, nutrients(ola).list().last().id)
    }

    @Test
    fun `a nutrient made by one user is hidden for others until they show it`() {
        val vitaminC = nutrients(kari).create(NutrientInput("Vitamin C", "mg", translations = listOf(NameTranslation("nb", "C-vitamin"))))
        assertEquals(vitaminC.id, nutrients(kari).list().last().id)
        assertFalse(nutrients(ola).list().any { it.id == vitaminC.id })
        nutrients(ola).setHidden(vitaminC.id, false)
        assertTrue(nutrients(ola).list().any { it.id == vitaminC.id })
    }

    @Test
    fun `an export has the units the data uses or the user shows, whoever made them`() {
        val slice = units(kari).create(UnitInput("slice", UnitKind.CUSTOM))
        units(kari).create(UnitInput("wedge", UnitKind.CUSTOM))
        units(ola).setHidden(unitId("cup"), true)
        FoodService(t.db, ola).create(FoodInput("Bread", 100.0, unitId("g"), units = listOf(FoodUnitLink(slice.id, 35.0, unitId("g")))))
        units(ola).setHidden(slice.id, true)

        val export = TransferService(t.db, ola).export()
        val names = export.units.associateBy { it.name }
        assertTrue(names.getValue("slice").hidden, "used, so included, but hidden for ola")
        assertFalse("wedge" in names, "kari's unit that ola neither uses nor shows")
        assertFalse("cup" in names)
        assertFalse(names.getValue("g").hidden)
    }

    @Test
    fun `importing matches the server's units by any name and never changes others' units`() {
        val slice = units(kari).create(UnitInput("slice", UnitKind.CUSTOM, plural = "slices"))
        FoodService(t.db, kari).create(FoodInput("Bread", 100.0, unitId("g"), units = listOf(FoodUnitLink(slice.id, 35.0, unitId("g")))))
        val export = TransferService(t.db, kari).export()
        val changed = export.copy(
            units = export.units.map { if (it.name == "slice") it.copy(plural = "slicez") else it } +
                ExportUnit("porsjon", UnitKind.CUSTOM),
        )

        val result = TransferService(t.db, ola).import(changed, ConflictStrategy.OVERWRITE)
        assertEquals(0, result.units.created, "'porsjon' is the built-in serving")
        assertEquals("slices", units(ola).get(slice.id).plural, "kari's unit isn't overwritten by ola")
        assertFalse(units(ola).get(slice.id).hidden, "but ola now shows it, like in the file")
        assertEquals(1, FoodService(t.db, ola).list().size)
    }

    @Test
    fun `version 2 files' archived units and nutrients are imported as hidden`() {
        val export = TransferService(t.db, admin).export()
        val v2 = export.copy(
            version = 2,
            units = export.units + ExportUnit("stone", UnitKind.MASS, 6350.0, archived = true),
            nutrients = export.nutrients + ExportNutrient("Iron", "mg", archived = true),
        )
        TransferService(t.db, kari).import(v2, ConflictStrategy.SKIP)
        assertTrue(units(kari).list(includeHidden = true).first { it.name == "stone" }.hidden)
        assertTrue(nutrients(kari).list(includeHidden = true).first { it.name == "Iron" }.hidden)
    }
}
