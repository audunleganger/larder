package com.caloriecompanion.shared

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodMetadataInput
import com.caloriecompanion.shared.api.MetadataInput
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.Seeder
import com.caloriecompanion.shared.service.TransferService
import com.caloriecompanion.shared.service.UnitService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Who made units, nutrients and foods and when, and who changed units and nutrients last. */
class MetadataTest {
    private val t = TestDb()
    private val admin = t.userId
    private var clock = 1_000L

    private fun user(name: String): Long {
        t.db.appUserQueries.insertUser(name, "x", false, "en", 0)
        val id = t.db.appUserQueries.lastInsertRowId().executeAsOne()
        Seeder.seed(t.db, id)
        return id
    }

    private val kari = user("kari")

    private fun units(user: Long) = UnitService(t.db, user, now = { clock })
    private fun nutrients(user: Long) = NutrientService(t.db, user, now = { clock })
    private fun foods(user: Long) = FoodService(t.db, user, now = { clock })

    private fun expectCode(code: String, block: () -> Unit): AppException =
        assertFailsWith<AppException> { block() }.also { assertEquals(code, it.code) }

    @Test
    fun `a new unit records who made it and when, and each edit who changed it`() {
        val made = units(kari).create(UnitInput("bowl", UnitKind.CUSTOM))
        assertEquals("kari", made.createdBy)
        assertEquals(1_000L, made.createdAt)
        assertNull(made.updatedAt)
        assertNull(made.updatedBy)

        clock = 2_000
        val edited = units(admin).update(made.id, UnitInput("bowl", UnitKind.CUSTOM, plural = "bowls"))
        assertEquals("kari", edited.createdBy)
        assertEquals(1_000L, edited.createdAt)
        assertEquals(2_000L, edited.updatedAt)
        assertEquals("local", edited.updatedBy)
    }

    @Test
    fun `hiding, showing and reordering don't count as changes`() {
        val unit = units(kari).create(UnitInput("bowl", UnitKind.CUSTOM))
        val nutrient = nutrients(kari).create(NutrientInput("Fibre", "g"))
        clock = 2_000
        units(kari).setHidden(unit.id, true)
        units(admin).setHidden(unit.id, false)
        nutrients(kari).setHidden(nutrient.id, true)
        nutrients(kari).setHidden(nutrient.id, false)
        nutrients(kari).reorder(listOf(nutrient.id))
        assertNull(units(kari).get(unit.id).updatedAt)
        assertNull(nutrients(kari).get(nutrient.id).updatedAt)

        nutrients(kari).update(nutrient.id, NutrientInput("Fibre", "g", 2))
        assertEquals(2_000L, nutrients(kari).get(nutrient.id).updatedAt)
        assertEquals("kari", nutrients(kari).get(nutrient.id).updatedBy)
    }

    @Test
    fun `a food records when it was made and by whom`() {
        val food = foods(kari).create(FoodInput("Porridge"))
        assertEquals(1_000L, food.createdAt)
        assertEquals("kari", food.createdBy)
    }

    @Test
    fun `admins can correct the metadata, and the new maker owns the item`() {
        val unit = units(admin).create(UnitInput("bowl", UnitKind.CUSTOM))
        val corrected = units(admin).setMetadata(unit.id, MetadataInput(500, "kari", 700, "local"))
        assertEquals("kari", corrected.createdBy)
        assertEquals(500L, corrected.createdAt)
        assertEquals(700L, corrected.updatedAt)
        assertEquals("local", corrected.updatedBy)
        // Correcting isn't a change itself.
        assertEquals(700L, units(admin).get(unit.id).updatedAt)
        // Kari now owns it, so she may edit it.
        units(kari).update(unit.id, UnitInput("bowl", UnitKind.CUSTOM))

        val nutrient = nutrients(admin).create(NutrientInput("Fibre", "g"))
        assertEquals("kari", nutrients(admin).setMetadata(nutrient.id, MetadataInput(500, "kari")).createdBy)
        assertNull(nutrients(admin).get(nutrient.id).updatedAt)
    }

    @Test
    fun `metadata corrections are checked`() {
        val unit = units(admin).create(UnitInput("bowl", UnitKind.CUSTOM))
        expectCode(ErrorCodes.FORBIDDEN) { units(kari).setMetadata(unit.id, MetadataInput(500, "kari")) }
        expectCode(ErrorCodes.VALIDATION) { units(admin).setMetadata(unit.id, MetadataInput(500, "nobody")) }
        expectCode(ErrorCodes.VALIDATION) { units(admin).setMetadata(unit.id, MetadataInput(500, "kari", 400)) }
        expectCode(ErrorCodes.VALIDATION) { units(admin).setMetadata(unit.id, MetadataInput(500, "kari", updatedBy = "kari")) }
        expectCode(ErrorCodes.VALIDATION) { units(admin).setMetadata(unit.id, MetadataInput(0, "kari")) }

        val food = foods(kari).create(FoodInput("Porridge"))
        expectCode(ErrorCodes.FORBIDDEN) { foods(kari).setMetadata(food.id, FoodMetadataInput(500)) }
        val own = foods(admin).create(FoodInput("Porridge"))
        assertEquals(500L, foods(admin).setMetadata(own.id, FoodMetadataInput(500)).createdAt)
    }

    @Test
    fun `an import keeps the dates from the file for new items`() {
        val unit = units(kari).create(UnitInput("bowl", UnitKind.CUSTOM))
        val parent = nutrients(kari).create(NutrientInput("Carbs", "g"))
        clock = 2_000
        units(kari).update(unit.id, UnitInput("bowl", UnitKind.CUSTOM))
        nutrients(kari).create(NutrientInput("Fibre", "g", parentId = parent.id))
        foods(kari).create(FoodInput("Porridge"))
        val file = TransferService(t.db, kari).export()
        assertEquals("1970-01-01T00:00:01Z", file.units.first { it.name == "bowl" }.createdAt)
        assertEquals("1970-01-01T00:00:02Z", file.units.first { it.name == "bowl" }.updatedAt)

        val other = TestDb()
        clock = 9_000
        TransferService(other.db, other.userId, now = { clock }).import(file, ConflictStrategy.SKIP)
        val bowl = UnitService(other.db, other.userId).list().first { it.name == "bowl" }
        assertEquals(1_000L, bowl.createdAt)
        assertEquals(2_000L, bowl.updatedAt)
        // The user who changed it isn't on this server; the importing user made it here.
        assertNull(bowl.updatedBy)
        assertEquals("local", bowl.createdBy)
        val fibre = NutrientService(other.db, other.userId).list().first { it.name == "Fibre" }
        assertEquals(2_000L, fibre.createdAt)
        // Linking it to its group on import isn't a change.
        assertNull(fibre.updatedAt)
        assertEquals(2_000L, FoodService(other.db, other.userId).list().first().let { FoodService(other.db, other.userId).get(it.id) }.createdAt)
    }
}
