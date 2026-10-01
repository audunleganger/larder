package com.caloriecompanion.server

import app.cash.sqldelight.db.QueryResult
import com.caloriecompanion.db.CalorieCompanionDatabase
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseTest {
    private fun userVersion(database: Database): Long =
        database.driver.executeQuery(null, "PRAGMA user_version", { QueryResult.Value(if (it.next().value) it.getLong(0) else null) }, 0).value!!

    @Test
    fun `a new database gets the current schema`() {
        val dir = Files.createTempDirectory("cc-db")
        Database(dir).use { assertEquals(CalorieCompanionDatabase.Schema.version, userVersion(it)) }
        assertTrue(dir.resolve("backups").toFile().listFiles().isNullOrEmpty())
    }

    @Test
    fun `an old database is backed up, then migrated`() {
        val dir = Files.createTempDirectory("cc-db")
        File("../shared/src/main/sqldelight/databases/1.db").copyTo(dir.resolve(Database.FILE_NAME).toFile())
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve(Database.FILE_NAME)}").use { it.createStatement().execute("PRAGMA user_version = 1") }

        Database(dir).use { assertEquals(CalorieCompanionDatabase.Schema.version, userVersion(it)) }

        val backups = dir.resolve("backups").listDirectoryEntries("pre-migration-v1-*.db")
        assertEquals(1, backups.size)
        DriverManager.getConnection("jdbc:sqlite:${backups.single()}").use {
            val rs = it.createStatement().executeQuery("PRAGMA user_version")
            rs.next()
            assertEquals(1, rs.getInt(1))
        }
    }

    @Test
    fun `a database from a newer version is refused`() {
        val dir = Files.createTempDirectory("cc-db")
        Database(dir).close()
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve(Database.FILE_NAME)}").use { it.createStatement().execute("PRAGMA user_version = 999") }
        assertFailsWith<IllegalStateException> { Database(dir) }
    }
}
