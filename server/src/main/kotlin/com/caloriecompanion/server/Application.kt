package com.caloriecompanion.server

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.AppInfo
import com.caloriecompanion.shared.api.HealthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: ServerConfig) {
    val driver = openDatabaseDriver(config.dataDir)
    monitor.subscribe(ApplicationStopped) { driver.close() }
    @Suppress("UNUSED_VARIABLE")
    val database = CalorieCompanionDatabase(driver)

    install(ContentNegotiation) { json() }
    install(CallLogging)

    routing {
        route("/api") {
            get("/health") {
                call.respond(HealthResponse(status = "ok", version = AppInfo.VERSION, apiVersion = AppInfo.API_VERSION))
            }
            // Unknown API paths must 404 rather than fall through to the web GUI's index.html.
            route("{...}") {
                handle { call.respond(HttpStatusCode.NotFound) }
            }
        }
        config.webDir?.let { dir ->
            singlePageApplication {
                filesPath = dir.toString()
            }
        }
    }
}
