package com.caloriecompanion.server

import com.caloriecompanion.shared.api.AdminUserCreate
import com.caloriecompanion.shared.api.AdminUserUpdate
import com.caloriecompanion.shared.api.DayView
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.EntryView
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.ErrorResponse
import com.caloriecompanion.shared.api.ExportFile
import com.caloriecompanion.shared.api.FoodDetail
import com.caloriecompanion.shared.api.FoodDto
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodNutrientValue
import com.caloriecompanion.shared.api.FoodSummary
import com.caloriecompanion.shared.api.FoodUnitLink
import com.caloriecompanion.shared.api.HistoryView
import com.caloriecompanion.shared.api.ImportResult
import com.caloriecompanion.shared.api.LoginInput
import com.caloriecompanion.shared.api.LoginResult
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.PasswordChangeInput
import com.caloriecompanion.shared.api.SetupInput
import com.caloriecompanion.shared.api.SetupStatus
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.api.TargetStatus
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UnitKind
import com.caloriecompanion.shared.api.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest {
    private fun ApplicationTestBuilder.jsonClient(): HttpClient {
        application { module(ServerConfig(dataDir = Files.createTempDirectory("cc-api"), bcryptCost = 4)) }
        return createClient { install(ContentNegotiation) { json(apiJson) } }
    }

    private fun HttpRequestBuilder.json(body: Any) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.error(): ErrorResponse = body()

    private suspend fun HttpClient.setup(): String {
        val result: LoginResult = post("/api/v1/setup") { json(SetupInput("admin", "correct horse", "en")) }.body()
        assertTrue(result.user.isAdmin)
        return result.token
    }

    @Test
    fun `first-run setup works once`() = testApplication {
        val client = jsonClient()
        assertEquals(true, client.get("/api/v1/setup").body<SetupStatus>().needsSetup)
        client.setup()
        assertEquals(false, client.get("/api/v1/setup").body<SetupStatus>().needsSetup)
        val again = client.post("/api/v1/setup") { json(SetupInput("evil", "password123")) }
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals(ErrorCodes.SETUP_DONE, again.error().error)
    }

    @Test
    fun `long multi-byte passwords are rejected cleanly, not with a 500`() = testApplication {
        val client = jsonClient()
        val long = "æøå".repeat(20) // 60 characters, 120 bytes
        val setup = client.post("/api/v1/setup") { json(SetupInput("admin", long)) }
        assertEquals(HttpStatusCode.BadRequest, setup.status)
        assertEquals(ErrorCodes.WEAK_PASSWORD, setup.error().error)
        client.setup()
        val login = client.post("/api/v1/auth/login") { json(LoginInput("admin", long)) }
        assertEquals(HttpStatusCode.Unauthorized, login.status)
    }

    @Test
    fun `endpoints require a session`() = testApplication {
        val client = jsonClient()
        client.setup()
        val response = client.get("/api/v1/foods")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ErrorCodes.UNAUTHORIZED, response.error().error)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/foods") { bearerAuth("bogus") }.status)
    }

    @Test
    fun `login, logout and password change`() = testApplication {
        val client = jsonClient()
        client.setup()
        val bad = client.post("/api/v1/auth/login") { json(LoginInput("admin", "wrong password")) }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
        assertEquals(ErrorCodes.INVALID_CREDENTIALS, bad.error().error)

        val token = client.post("/api/v1/auth/login") { json(LoginInput("ADMIN", "correct horse")) }.body<LoginResult>().token
        assertEquals("admin", client.get("/api/v1/me") { bearerAuth(token) }.body<UserDto>().username)

        val weak = client.put("/api/v1/me/password") { bearerAuth(token); json(PasswordChangeInput("correct horse", "short")) }
        assertEquals(ErrorCodes.WEAK_PASSWORD, weak.error().error)
        assertEquals(
            HttpStatusCode.NoContent,
            client.put("/api/v1/me/password") { bearerAuth(token); json(PasswordChangeInput("correct horse", "battery staple")) }.status,
        )
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/auth/login") { json(LoginInput("admin", "battery staple")) }.status)

        client.post("/api/v1/auth/logout") { bearerAuth(token) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(token) }.status)
    }

    @Test
    fun `admin manages users, users have separate catalogs`() = testApplication {
        val client = jsonClient()
        val admin = client.setup()
        val created = client.post("/api/v1/admin/users") { bearerAuth(admin); json(AdminUserCreate("kari", "passord123", locale = "nb")) }
        assertEquals(HttpStatusCode.Created, created.status)
        val kari = created.body<UserDto>()

        val kariToken = client.post("/api/v1/auth/login") { json(LoginInput("kari", "passord123")) }.body<LoginResult>().token
        // Seeded in Norwegian
        assertTrue(client.get("/api/v1/nutrients") { bearerAuth(kariToken) }.body<List<NutrientDto>>().any { it.name == "Energi" })
        // Non-admins can't manage users
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/admin/users") { bearerAuth(kariToken) }.status)

        // Separate catalogs: kari's food isn't visible to admin
        client.post("/api/v1/foods") { bearerAuth(kariToken); json(FoodInput("Brunost")) }
        assertTrue(client.get("/api/v1/foods") { bearerAuth(admin) }.body<List<FoodSummary>>().isEmpty())

        // The last admin can't be demoted
        val adminUser = client.get("/api/v1/me") { bearerAuth(admin) }.body<UserDto>()
        val demote = client.patch("/api/v1/admin/users/${adminUser.id}") { bearerAuth(admin); json(AdminUserUpdate(isAdmin = false)) }
        assertEquals(ErrorCodes.LAST_ADMIN, demote.error().error)

        // Disabling a user ends their sessions
        client.patch("/api/v1/admin/users/${kari.id}") { bearerAuth(admin); json(AdminUserUpdate(isDisabled = true)) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me") { bearerAuth(kariToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/v1/auth/login") { json(LoginInput("kari", "passord123")) }.status)

        // Password reset by admin
        client.patch("/api/v1/admin/users/${kari.id}") { bearerAuth(admin); json(AdminUserUpdate(isDisabled = false, password = "nytt passord")) }
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/auth/login") { json(LoginInput("kari", "nytt passord")) }.status)

        assertEquals(HttpStatusCode.OK, client.post("/api/v1/admin/backup") { bearerAuth(admin) }.status)
    }

    @Test
    fun `catalog to day view round trip`() = testApplication {
        val client = jsonClient()
        val token = client.setup()
        val units = client.get("/api/v1/units") { bearerAuth(token) }.body<List<UnitDto>>()
        val nutrients = client.get("/api/v1/nutrients") { bearerAuth(token) }.body<List<NutrientDto>>()
        val g = units.first { it.name == "g" }.id
        val energy = nutrients.first { it.name == "Energy" }.id

        val slice = client.post("/api/v1/units") { bearerAuth(token); json(UnitInput("slice", UnitKind.CUSTOM)) }.body<UnitDto>()
        val dup = client.post("/api/v1/units") { bearerAuth(token); json(UnitInput("Slice", UnitKind.CUSTOM)) }
        assertEquals(HttpStatusCode.Conflict, dup.status)
        assertEquals(ErrorCodes.NAME_TAKEN, dup.error().error)

        val bread = client.post("/api/v1/foods") {
            bearerAuth(token)
            json(FoodInput("Bread", 100.0, g, nutrients = listOf(FoodNutrientValue(energy, 250.0)), units = listOf(FoodUnitLink(slice.id, 35.0, g))))
        }.body<FoodDto>()

        val entry = client.post("/api/v1/entries") { bearerAuth(token); json(EntryInput(bread.id, slice.id, 2.0, "2026-09-30", "08:15")) }
        assertEquals(HttpStatusCode.Created, entry.status)
        assertEquals(175.0, entry.body<EntryView>().nutrients.first { it.nutrientId == energy }.amount)

        client.post("/api/v1/targets") { bearerAuth(token); json(TargetInput(energy, 2000.0, 2500.0, "2026-01-01")) }
        val day = client.get("/api/v1/days/2026-09-30") { bearerAuth(token) }.body<DayView>()
        val total = day.totals.first { it.nutrientId == energy }
        assertEquals(175.0, total.amount)
        assertEquals(TargetStatus.BELOW, total.status)

        val detail = client.get("/api/v1/foods/${bread.id}") { bearerAuth(token) }.body<FoodDetail>()
        assertEquals("slice", detail.entries.single().unitName)

        val referenced = client.delete("/api/v1/foods/${bread.id}") { bearerAuth(token) }
        assertEquals(HttpStatusCode.Conflict, referenced.status)
        assertEquals(1L, referenced.error().details["entries"])

        val history = client.get("/api/v1/history?from=2026-09-24&to=2026-09-30") { bearerAuth(token) }.body<HistoryView>()
        assertEquals(7, history.days.size)

        val invalid = client.post("/api/v1/entries") { bearerAuth(token); json(EntryInput(bread.id, slice.id, -1.0, "2026-09-30", "08:15")) }
        assertEquals(ErrorCodes.VALIDATION, invalid.error().error)
        val malformed = client.post("/api/v1/entries") { bearerAuth(token); contentType(ContentType.Application.Json); setBody("{\"foodId\": \"x\"}") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)

        // Export and re-import (skip) is idempotent
        val export = client.get("/api/v1/export") { bearerAuth(token) }.body<ExportFile>()
        assertEquals(1, export.entries.size)
        val result = client.post("/api/v1/import?onConflict=skip") { bearerAuth(token); json(export) }.body<ImportResult>()
        assertEquals(1, result.entries.skipped)
        assertEquals(0, result.entries.created)

        val bad = client.post("/api/v1/import") { bearerAuth(token); contentType(ContentType.Application.Json); setBody("{\"format\": \"nope\"}") }
        assertEquals(ErrorCodes.INVALID_IMPORT, bad.error().error)
    }
}
