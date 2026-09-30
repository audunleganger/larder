package com.caloriecompanion.server

import com.caloriecompanion.shared.AppInfo
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.ErrorResponse
import com.caloriecompanion.shared.api.HealthResponse
import com.caloriecompanion.shared.domain.AppException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

val apiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config)
    }.start(wait = false)
    // Keep running until the JVM shuts down (SIGTERM/SIGINT). Ktor's shutdown hook then stops the
    // server exactly once; with start(wait = true) the main thread stopped it concurrently with the
    // hook, which could leave the process hanging on shutdown.
    Thread.currentThread().join()
}

fun Application.module(config: ServerConfig) {
    val database = Database(config.dataDir)
    monitor.subscribe(ApplicationStopped) { database.close() }
    val auth = Auth(config)

    install(ContentNegotiation) { json(apiJson) }
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path().startsWith("/api") }
    }
    install(StatusPages) {
        exception<AppException> { call, e ->
            call.respond(HttpStatusCode.fromValue(e.status), ErrorResponse(e.code, e.message ?: e.code, e.details))
        }
        exception<BadRequestException> { call, e ->
            val cause = generateSequence<Throwable>(e) { it.cause }.firstOrNull { it is SerializationException }
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorCodes.VALIDATION, cause?.message ?: e.message ?: "Bad request"))
        }
        exception<SerializationException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorCodes.VALIDATION, e.message ?: "Invalid JSON"))
        }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL", "Internal server error"))
        }
    }

    routing {
        route("/api") {
            get("/health") {
                call.respond(HealthResponse(status = "ok", version = AppInfo.VERSION, apiVersion = AppInfo.API_VERSION))
            }
            route("/v1") {
                accountRoutes(database, auth)
                adminRoutes(database, auth)
                catalogRoutes(database, auth)
                diaryRoutes(database, auth)
            }
            // Unknown API paths must 404 rather than fall through to the web GUI's index.html.
            route("{...}") {
                handle { call.respond(HttpStatusCode.NotFound, ErrorResponse(ErrorCodes.NOT_FOUND, "No such endpoint")) }
            }
        }
        config.webDir?.let { dir ->
            singlePageApplication {
                filesPath = dir.toString()
            }
        }
    }
}
