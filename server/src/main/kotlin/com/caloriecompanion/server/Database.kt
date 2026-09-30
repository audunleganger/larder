package com.caloriecompanion.server

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.caloriecompanion.db.CalorieCompanionDatabase
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize

/**
 * The server's SQLite database. All access runs on one dedicated thread: SQLite serializes writes
 * anyway, and it keeps transactions and connections simple.
 */
class Database(private val dataDir: Path) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "database") }
    private val dispatcher = executor.asCoroutineDispatcher()

    val driver: SqlDriver = kotlin.run {
        dataDir.createDirectories()
        JdbcSqliteDriver(
            url = "jdbc:sqlite:${dataDir.resolve(FILE_NAME)}",
            properties = Properties().apply {
                put("foreign_keys", "true")
                put("journal_mode", "WAL")
                put("busy_timeout", "5000")
            },
            schema = CalorieCompanionDatabase.Schema,
        )
    }
    val queries = CalorieCompanionDatabase(driver)

    suspend fun <T> run(block: (CalorieCompanionDatabase) -> T): T = withContext(dispatcher) { block(queries) }

    /** Writes a consistent copy of the database to data/backups (DEP-4). */
    suspend fun backup(): Path = withContext(dispatcher) {
        val dir = dataDir.resolve("backups").createDirectories()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val target = dir.resolve("calorie-companion-$stamp.db")
        driver.execute(null, "VACUUM INTO '${target.toString().replace("'", "''")}'", 0)
        target
    }

    fun sizeOf(path: Path): Long = path.fileSize()

    override fun close() {
        driver.close()
        executor.shutdown()
    }

    companion object {
        const val FILE_NAME = "calorie-companion.db"
    }
}
