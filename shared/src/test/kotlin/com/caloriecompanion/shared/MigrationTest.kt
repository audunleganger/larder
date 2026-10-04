package com.caloriecompanion.shared

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.TargetStatus
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.FoodService
import com.caloriecompanion.shared.service.NutrientService
import com.caloriecompanion.shared.service.UnitService
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Upgrades a database created with the version 1 schema (as deployed) and filled with data, and checks
 * that the data survives and the app works on it. verifySqlDelightMigration separately proves the
 * resulting schema equals a freshly created one.
 */
class MigrationTest {
    private fun v1Database(): SqlDriver {
        val file = createTempFile("v1", ".db").toFile().apply { deleteOnExit() }
        File("src/main/sqldelight/databases/1.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.path}", Properties().apply { put("foreign_keys", "true") })
        V1_DATA.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { driver.execute(null, it, 0) }
        return driver
    }

    private fun SqlDriver.long(sql: String): Long =
        executeQuery(null, sql, { QueryResult.Value(if (it.next().value) it.getLong(0) else null) }, 0).value!!

    @Test
    fun `version 1 data survives the upgrade`() {
        val driver = v1Database()
        val schema = CalorieCompanionDatabase.Schema
        CalorieCompanionDatabase(driver).transaction { schema.migrate(driver, 1, schema.version) }
        val db = CalorieCompanionDatabase(driver)

        val day = EntryService(db, 1).day("2026-10-01")
        assertEquals(listOf("Rye bread"), day.entries.map { it.foodName })
        // 2 slices x 35 g of 250 kcal / 100 g.
        assertEquals(175.0, day.totals.first { it.nutrientId == 1L }.amount, 1e-9)
        assertEquals(1L, driver.long("SELECT count(*) FROM food"))
        // v2: no remembered reference yet, so new foods start at 100 g.
        assertEquals(100.0, FoodService(db, 1).refDefault().refAmount)
        // v3: plural endings and the built-in names in the other language.
        val units = UnitService(db, 1, "nb").list()
        assertEquals("s", units.first { it.name == "slice" }.pluralSuffix)
        assertEquals("", units.first { it.name == "g" }.pluralSuffix)
        assertEquals(listOf("Energi", "Karbohydrater", "Sukkerarter"), NutrientService(db, 1, "nb").list().map { it.displayName })
        // v7: existing items count as made at the upgrade, by their owner, and never changed.
        val upgradedAt = System.currentTimeMillis()
        val slice = units.first { it.name == "slice" }
        assertTrue(slice.createdAt in upgradedAt - 60_000..upgradedAt)
        assertEquals(null, slice.updatedAt)
        assertTrue(FoodService(db, 1).list().all { FoodService(db, 1).get(it.id).createdAt in upgradedAt - 60_000..upgradedAt })
        // v8: no food is ingredient only.
        assertTrue(FoodService(db, 1).list().none { it.ingredientOnly })
        // v9: no unit order of their own yet, so the units are alphabetical.
        assertTrue(units.all { it.sortOrder == null })
    }

    @Test
    fun `two users' units and nutrients are merged into one shared set`() {
        val driver = v1Database()
        TWO_USER_DATA.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { driver.execute(null, it, 0) }
        val schema = CalorieCompanionDatabase.Schema
        CalorieCompanionDatabase(driver).transaction { schema.migrate(driver, 1, schema.version) }
        val db = CalorieCompanionDatabase(driver)

        // One "g", owned by the admin, seen by both; Kari's food and entry now use it.
        assertEquals(1L, driver.long("SELECT count(*) FROM quantity_unit WHERE name_norm = 'g'"))
        val units = UnitService(db, 2).list()
        val g = units.first { it.name == "g" }
        assertEquals("audun", g.createdBy)
        assertTrue(g.builtIn)
        assertEquals(1L, g.id)
        assertEquals(0L, driver.long("SELECT count(*) FROM food WHERE ref_unit_id <> 1"))
        // Kari's Norwegian "stk" is the same unit as Audun's "piece", called "stk" in Norwegian since v3.
        assertEquals("piece", units.first { it.id == driver.long("SELECT unit_id FROM entry WHERE user_id = 2") }.name)
        // Each user shows the units they had: Kari's "stk" and archived "glass" aren't Audun's.
        assertFalse(UnitService(db, 1).list().any { it.name == "cup" }, "Audun had archived his cup")
        assertTrue(UnitService(db, 1).list(includeHidden = true).first { it.name == "glass" }.hidden)
        assertFalse(units.first { it.name == "glass" }.hidden)
        assertFalse(units.first { it.name == "glass" }.builtIn)
        assertEquals("kari", units.first { it.name == "glass" }.createdBy)

        // Nutrients: one Energy; Kari keeps her order and her target.
        assertEquals(1L, driver.long("SELECT count(*) FROM nutrient WHERE name_norm = 'energy'"))
        assertEquals(listOf("Carbohydrates", "Energy"), NutrientService(db, 2).list().map { it.name })
        assertEquals(listOf("Energy", "Carbohydrates", "Sugars"), NutrientService(db, 1).list().map { it.name })
        val day = EntryService(db, 2).day("2026-10-02")
        // 2 stk x 60 g of 142 kcal / 100 g.
        assertEquals(170.4, day.totals.first { it.nutrientId == 1L }.amount, 1e-9)
        assertEquals(TargetStatus.BELOW, day.totals.first { it.nutrientId == 1L }.status)
        // The first user's day is unchanged.
        assertEquals(175.0, EntryService(db, 1).day("2026-10-01").totals.first { it.nutrientId == 1L }.amount, 1e-9)
    }

    companion object {
        /**
         * A second user's catalog, as created in Norwegian with the version 1 schema: the same "g" and
         * "Energy" as the first user's, a "stk", an archived "cup" for the first user, and a "glass".
         */
        private val TWO_USER_DATA = """
            INSERT INTO app_user(id, username, password_hash, is_admin, is_disabled, locale, created_at) VALUES (2, 'kari', 'x', 0, 0, 'nb', 0);
            INSERT INTO quantity_unit(id, user_id, name, name_norm, kind, base_factor, archived) VALUES (3, 1, 'piece', 'piece', 'custom', NULL, 0);
            INSERT INTO quantity_unit(id, user_id, name, name_norm, kind, base_factor, archived) VALUES (4, 1, 'cup', 'cup', 'volume', 250.0, 1);
            INSERT INTO quantity_unit(id, user_id, name, name_norm, kind, base_factor, archived) VALUES (10, 2, 'g', 'g', 'mass', 1.0, 0);
            INSERT INTO quantity_unit(id, user_id, name, name_norm, kind, base_factor, archived) VALUES (11, 2, 'stk', 'stk', 'custom', NULL, 0);
            INSERT INTO quantity_unit(id, user_id, name, name_norm, kind, base_factor, archived) VALUES (12, 2, 'glass', 'glass', 'custom', NULL, 0);
            INSERT INTO nutrient(id, user_id, name, name_norm, measure_unit, display_precision, sort_order, parent_id, archived) VALUES (10, 2, 'Energy', 'energy', 'kcal', 0, 1, NULL, 0);
            INSERT INTO nutrient(id, user_id, name, name_norm, measure_unit, display_precision, sort_order, parent_id, archived) VALUES (11, 2, 'Carbohydrates', 'carbohydrates', 'g', 1, 0, NULL, 0);
            INSERT INTO food(id, user_id, name, name_norm, ref_amount, ref_unit_id, notes, archived) VALUES (10, 2, 'Egg', 'egg', 100.0, 10, NULL, 0);
            INSERT INTO food_unit(food_id, unit_id, equals_amount, equals_unit_id) VALUES (10, 11, 60.0, 10);
            INSERT INTO food_nutrient(food_id, nutrient_id, amount) VALUES (10, 10, 142.0);
            INSERT INTO entry(user_id, food_id, unit_id, quantity, local_date, local_time, note, created_at, updated_at) VALUES (2, 10, 11, 2.0, '2026-10-02', '08:00', NULL, 0, 0);
            INSERT INTO target(user_id, nutrient_id, min_amount, max_amount, effective_from) VALUES (2, 10, 1800.0, 2200.0, '2026-01-01');
        """

        /** A small catalog and one entry, written with the version 1 schema. */
        private val V1_DATA = """
            INSERT INTO app_user(id, username, password_hash, is_admin, is_disabled, locale, created_at) VALUES (1, 'audun', 'x', 1, 0, 'en', 0);
            INSERT INTO quantity_unit(id, user_id, name, name_norm, kind, base_factor, archived) VALUES (1, 1, 'g', 'g', 'mass', 1.0, 0);
            INSERT INTO quantity_unit(id, user_id, name, name_norm, kind, base_factor, archived) VALUES (2, 1, 'slice', 'slice', 'custom', NULL, 0);
            INSERT INTO nutrient(id, user_id, name, name_norm, measure_unit, display_precision, sort_order, parent_id, archived) VALUES (1, 1, 'Energy', 'energy', 'kcal', 0, 0, NULL, 0);
            INSERT INTO nutrient(id, user_id, name, name_norm, measure_unit, display_precision, sort_order, parent_id, archived) VALUES (2, 1, 'Carbohydrates', 'carbohydrates', 'g', 1, 1, NULL, 0);
            INSERT INTO nutrient(id, user_id, name, name_norm, measure_unit, display_precision, sort_order, parent_id, archived) VALUES (3, 1, 'Sugars', 'sugars', 'g', 1, 2, 2, 0);
            INSERT INTO food(id, user_id, name, name_norm, ref_amount, ref_unit_id, notes, archived) VALUES (1, 1, 'Rye bread', 'rye bread', 100.0, 1, NULL, 0);
            INSERT INTO food_unit(food_id, unit_id, equals_amount, equals_unit_id) VALUES (1, 2, 35.0, 1);
            INSERT INTO food_nutrient(food_id, nutrient_id, amount) VALUES (1, 1, 250.0);
            INSERT INTO entry(user_id, food_id, unit_id, quantity, local_date, local_time, note, created_at, updated_at) VALUES (1, 1, 2, 2.0, '2026-10-01', '12:00', NULL, 0, 0);
            INSERT INTO target(user_id, nutrient_id, min_amount, max_amount, effective_from) VALUES (1, 1, 1800.0, 2200.0, '2026-01-01');
        """
    }
}
