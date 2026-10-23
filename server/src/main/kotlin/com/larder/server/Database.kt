package com.larder.server

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.larder.db.LarderDatabase
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
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
            url = "jdbc:sqlite:${fileIn(dataDir)}",
            properties = Properties().apply {
                put("foreign_keys", "true")
                put("journal_mode", "WAL")
                put("busy_timeout", "5000")
            },
        ).also(::createOrMigrate)
    }
    val queries = LarderDatabase(driver)

    suspend fun <T> run(block: (LarderDatabase) -> T): T = withContext(dispatcher) { block(queries) }

    /** Writes a consistent copy of the database to data/backups (DEP-4). */
    suspend fun backup(): Path = withContext(dispatcher) { backupTo(driver, "larder") }

    private fun backupTo(driver: SqlDriver, prefix: String): Path {
        val dir = dataDir.resolve("backups").createDirectories()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val target = dir.resolve("$prefix-$stamp.db")
        driver.execute(null, "VACUUM INTO '${target.toString().replace("'", "''")}'", 0)
        return target
    }

    /**
     * Creates the schema in a new database, or brings an older one up to date. Before migrating, a
     * copy of the old database is saved to data/backups, and the migration runs in one transaction,
     * so a failed upgrade leaves the database as it was.
     */
    private fun createOrMigrate(driver: SqlDriver) {
        val schema = LarderDatabase.Schema
        val current = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        }, 0).value
        when {
            current == 0L -> LarderDatabase(driver).transaction {
                schema.create(driver)
                driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
            }
            current < schema.version -> {
                val backup = backupTo(driver, "pre-migration-v$current")
                log.info("Migrating database from schema version $current to ${schema.version} (backup: $backup)")
                LarderDatabase(driver).transaction {
                    schema.migrate(driver, current, schema.version)
                    driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
                }
            }
            current > schema.version -> error(
                "The database has schema version $current, but this server only knows up to ${schema.version}. " +
                    "Run a newer server version or restore a backup.",
            )
        }
    }

    fun sizeOf(path: Path): Long = path.fileSize()

    override fun close() {
        driver.close()
        executor.shutdown()
    }

    companion object {
        const val FILE_NAME = "larder.db"

        /** The file name from before the rename to Larder. */
        const val LEGACY_FILE_NAME = "calorie-companion.db"
        private val log = LoggerFactory.getLogger(Database::class.java)

        /** The database file in [dataDir]: [FILE_NAME], or [LEGACY_FILE_NAME] if only that one exists. */
        fun fileIn(dataDir: Path): Path {
            val legacy = dataDir.resolve(LEGACY_FILE_NAME)
            val current = dataDir.resolve(FILE_NAME)
            if (current.exists() || !legacy.exists()) return current
            log.info("Opening $legacy; rename it to $FILE_NAME while the server is stopped")
            return legacy
        }
    }
}
