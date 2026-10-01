package com.caloriecompanion.shared

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.FoodService
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

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
    }

    companion object {
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
