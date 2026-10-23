package com.larder.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.larder.db.LarderDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaTest {
    @Test
    fun `schema creates on an empty database`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            LarderDatabase.Schema.create(driver)
            val db = LarderDatabase(driver)
            assertEquals(0L, db.appUserQueries.countUsers().executeAsOne())
        }
    }
}
