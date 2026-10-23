package com.larder.shared

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.larder.db.LarderDatabase
import com.larder.shared.service.LocalUser
import java.util.Properties

/** A fresh in-memory database with one seeded user ([locale]: English by default). */
class TestDb(locale: String = "en") {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        .also { LarderDatabase.Schema.create(it) }
    val db = LarderDatabase(driver)
    val userId = LocalUser.ensure(db, locale)
}
