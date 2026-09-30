package com.caloriecompanion.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.caloriecompanion.db.CalorieCompanionDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaTest {
    @Test
    fun `schema creates on an empty database`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            CalorieCompanionDatabase.Schema.create(driver)
            val db = CalorieCompanionDatabase(driver)
            assertEquals(0L, db.appUserQueries.countUsers().executeAsOne())
        }
    }
}
