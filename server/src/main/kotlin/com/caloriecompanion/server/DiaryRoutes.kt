package com.caloriecompanion.server

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.domain.AppException
import io.ktor.server.plugins.BadRequestException
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.ExportFile
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.domain.validation
import com.caloriecompanion.shared.service.EntryService
import com.caloriecompanion.shared.service.HistoryService
import com.caloriecompanion.shared.service.TargetService
import com.caloriecompanion.shared.service.TransferService
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.time.LocalDate

/** Entries, day view, targets, history and import/export (E-*, D-*, T-*, H-*, X-*). */
fun Route.diaryRoutes(database: Database, auth: Auth) {
    get("/days/{date}") {
        val date = call.parameters["date"]!!
        call.respond(call.withUserLanguage(database, auth) { db, user, language -> EntryService(db, user, language = language).day(date) })
    }

    route("/entries") {
        post {
            val input = call.receive<EntryInput>()
            call.respond(HttpStatusCode.Created, call.withUserLanguage(database, auth) { db, user, language -> EntryService(db, user, language = language).create(input) })
        }
        post("/preview") {
            val input = call.receive<PreviewInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> EntryService(db, user, language = language).preview(input) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> EntryService(db, user, language = language).get(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<EntryInput>()
            call.respond(call.withUserLanguage(database, auth) { db, user, language -> EntryService(db, user, language = language).update(id, input) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUserLanguage(database, auth) { db, user, language -> EntryService(db, user, language = language).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/targets") {
        get {
            call.respond(call.withUser(database, auth) { db, user -> TargetService(db, user).list() })
        }
        post {
            val input = call.receive<TargetInput>()
            call.respond(call.withUser(database, auth) { db, user -> TargetService(db, user).set(input) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.withUser(database, auth) { db, user -> TargetService(db, user).delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    get("/history") {
        val from = call.request.queryParameters["from"] ?: validation("'from' is required")
        val to = call.request.queryParameters["to"] ?: validation("'to' is required")
        call.respond(call.withUser(database, auth) { db, user -> HistoryService(db, user).history(from, to) })
    }

    get("/export") {
        val file = call.withUser(database, auth) { db, user -> TransferService(db, user).export() }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "calorie-companion-${LocalDate.now()}.json").toString(),
        )
        call.respond(file)
    }

    post("/import") {
        val strategy = when (call.request.queryParameters["onConflict"]?.lowercase()) {
            null, "skip" -> ConflictStrategy.SKIP
            "overwrite" -> ConflictStrategy.OVERWRITE
            else -> validation("onConflict must be 'skip' or 'overwrite'")
        }
        val file = try {
            call.receive<ExportFile>()
        } catch (e: BadRequestException) {
            throw AppException(ErrorCodes.INVALID_IMPORT, "Not a valid Calorie Companion export file", 400)
        }
        call.respond(call.withUser(database, auth) { db, user -> TransferService(db, user).import(file, strategy) })
    }
}
