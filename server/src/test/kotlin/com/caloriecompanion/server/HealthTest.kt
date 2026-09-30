package com.caloriecompanion.server

import com.caloriecompanion.shared.AppInfo
import com.caloriecompanion.shared.api.HealthResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun `health endpoint reports version`() = testApplication {
        val dataDir = Files.createTempDirectory("cc-test")
        application { module(ServerConfig(host = "localhost", port = 0, dataDir = dataDir, webDir = null)) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HealthResponse("ok", AppInfo.VERSION, AppInfo.API_VERSION), response.body())
    }

    @Test
    fun `unknown api paths 404 even when the web GUI is served`() = testApplication {
        val dataDir = Files.createTempDirectory("cc-test")
        val webDir = Files.createTempDirectory("cc-web").also { it.resolve("index.html").writeText("<html></html>") }
        application { module(ServerConfig(host = "localhost", port = 0, dataDir = dataDir, webDir = webDir)) }

        assertEquals(HttpStatusCode.NotFound, client.get("/api/nope").status)
        assertEquals(HttpStatusCode.OK, client.get("/some/client/route").status)
    }
}
