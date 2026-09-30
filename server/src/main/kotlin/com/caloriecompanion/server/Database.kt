package com.caloriecompanion.server

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.caloriecompanion.db.CalorieCompanionDatabase
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.createDirectories

/** Opens (creating or migrating as needed) the SQLite database in [dataDir]. */
fun openDatabaseDriver(dataDir: Path): SqlDriver {
    dataDir.createDirectories()
    val file = dataDir.resolve("calorie-companion.db")
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:$file",
        properties = Properties().apply { put("foreign_keys", "true") },
        schema = CalorieCompanionDatabase.Schema,
    )
}
