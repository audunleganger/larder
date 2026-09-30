package com.caloriecompanion.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.service.LocalUser
import java.util.Properties

/** A fresh in-memory database with one seeded (English) user. */
class TestDb {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        .also { CalorieCompanionDatabase.Schema.create(it) }
    val db = CalorieCompanionDatabase(driver)
    val userId = LocalUser.ensure(db, "en")
}
