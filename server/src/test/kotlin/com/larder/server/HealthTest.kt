package com.larder.server

import com.larder.shared.AppInfo
import com.larder.shared.api.HealthResponse
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
        application { module(ServerConfig(dataDir = dataDir, bcryptCost = 4)) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HealthResponse("ok", AppInfo.VERSION, AppInfo.API_VERSION), response.body())
    }

    @Test
    fun `health reports the configured version`() = testApplication {
        val dataDir = Files.createTempDirectory("cc-test")
        application { module(ServerConfig(dataDir = dataDir, bcryptCost = 4, version = "1.2.3")) }
        val client = createClient { install(ContentNegotiation) { json() } }

        assertEquals("1.2.3", client.get("/api/health").body<HealthResponse>().version)
    }

    @Test
    fun `version comes from LARDER_VERSION, or the source version when unset or blank`() {
        assertEquals("0.10.0-test", ServerConfig.fromEnv(mapOf("LARDER_VERSION" to "0.10.0-test")).version)
        assertEquals(AppInfo.VERSION, ServerConfig.fromEnv(mapOf("LARDER_VERSION" to "")).version)
        assertEquals(AppInfo.VERSION, ServerConfig.fromEnv(emptyMap()).version)
    }

    @Test
    fun `CC_ settings from before the rename to Larder still work, LARDER_ ones win`() {
        assertEquals(9090, ServerConfig.fromEnv(mapOf("CC_PORT" to "9090")).port)
        assertEquals(9191, ServerConfig.fromEnv(mapOf("CC_PORT" to "9090", "LARDER_PORT" to "9191")).port)
    }

    @Test
    fun `unknown api paths 404 even when the web GUI is served`() = testApplication {
        val dataDir = Files.createTempDirectory("cc-test")
        val webDir = Files.createTempDirectory("cc-web").also { it.resolve("index.html").writeText("<html></html>") }
        application { module(ServerConfig(dataDir = dataDir, webDir = webDir, bcryptCost = 4)) }

        assertEquals(HttpStatusCode.NotFound, client.get("/api/nope").status)
        assertEquals(HttpStatusCode.OK, client.get("/some/client/route").status)
    }
}
