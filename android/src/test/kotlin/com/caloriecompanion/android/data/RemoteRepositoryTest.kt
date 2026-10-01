package com.caloriecompanion.android.data

import com.caloriecompanion.server.ServerConfig
import com.caloriecompanion.server.module
import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.LoginResult
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.SetupInput
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.api.TargetStatus
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Runs the Android server-mode client against the real server, in-process. */
class RemoteRepositoryTest {
    private suspend fun ApplicationTestBuilder.repository(): RemoteRepository {
        application { module(ServerConfig(dataDir = Files.createTempDirectory("cc-android"), bcryptCost = 4)) }
        val json = createClient { install(ContentNegotiation) { json(apiJson) } }
        val login = json.post("/api/v1/setup") {
            contentType(ContentType.Application.Json)
            setBody(SetupInput("admin", "password123", "en"))
        }.body<LoginResult>()
        return RemoteRepository("http://localhost", login.token, client.engine)
    }

    @Test
    fun `full diary round trip over HTTP`() = testApplication {
        val repo = repository()
        assertEquals(1, repo.health().apiVersion)
        val g = repo.units().first { it.name == "g" }.id
        val energy = repo.nutrients().first { it.name == "Energy" }.id
        val slice = repo.createUnit(UnitInput("slice", UnitKind.CUSTOM)).id
        val bread = repo.createFood(
            FoodInput("Bread", 100.0, g, nutrients = listOf(FoodNutrientValue(energy, 250.0)), units = listOf(FoodUnitLink(slice, 35.0, g))),
        )
        assertEquals(175.0, repo.preview(PreviewInput(bread.id, slice, 2.0)).nutrients.first { it.nutrientId == energy }.amount)

        val entry = repo.createEntry(EntryInput(bread.id, slice, 2.0, "2026-09-30", "08:00"))
        assertEquals(entry, repo.entry(entry.id))
        repo.setTarget(TargetInput(energy, 100.0, 200.0, "2026-01-01"))
        val total = repo.day("2026-09-30").totals.first { it.nutrientId == energy }
        assertEquals(175.0, total.amount)
        assertEquals(TargetStatus.WITHIN, total.status)

        assertEquals("slice", repo.foodDetail(bread.id).entries.single().unitName)
        assertEquals(listOf("2026-09-30"), repo.unitDetail(slice).dates)
        assertEquals(7, repo.history("2026-09-24", "2026-09-30").days.size)
        assertTrue(repo.foods("brea").any { it.id == bread.id })

        val export = repo.export()
        assertEquals(1, repo.import(export, ConflictStrategy.SKIP).entries.skipped)

        repo.updateEntry(entry.id, EntryInput(bread.id, g, 100.0, "2026-09-30", "09:00"))
        assertEquals(250.0, repo.day("2026-09-30").totals.first { it.nutrientId == energy }.amount)
        repo.deleteEntry(entry.id)
        assertTrue(repo.day("2026-09-30").entries.isEmpty())
    }

    @Test
    fun `server errors become repository exceptions with codes`() = testApplication {
        val repo = repository()
        repo.createFood(FoodInput("Milk"))
        val taken = assertFailsWith<RepositoryException> { repo.createFood(FoodInput(" milk")) }
        assertEquals(ErrorCodes.NAME_TAKEN, taken.code)

        val food = repo.createFood(FoodInput("Soup"))
        val serving = repo.units().first { it.name == "serving" }.id
        repo.createEntry(EntryInput(food.id, serving, 1.0, "2026-09-30", "12:00"))
        val referenced = assertFailsWith<RepositoryException> { repo.deleteFood(food.id) }
        assertEquals(ErrorCodes.REFERENCED, referenced.code)
        assertEquals(1L, referenced.details["entries"])
    }

    @Test
    fun `expired session triggers the unauthorized callback`() = testApplication {
        val repo = repository()
        var called = false
        repo.onUnauthorized = { called = true }
        repo.logout()
        val e = assertFailsWith<RepositoryException> { repo.units() }
        assertEquals(ErrorCodes.UNAUTHORIZED, e.code)
        assertTrue(called)
    }

    @Test
    fun `server addresses are normalized`() {
        assertEquals("http://192.168.1.10:8080", normalizeServerUrl(" 192.168.1.10:8080/ "))
        assertEquals("https://food.example.com", normalizeServerUrl("https://food.example.com/"))
    }
}
