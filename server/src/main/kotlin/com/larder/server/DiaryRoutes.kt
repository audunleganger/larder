package com.larder.server

import com.larder.shared.api.ConflictStrategy
import com.larder.shared.api.ErrorCodes
import com.larder.shared.domain.AppException
import io.ktor.server.plugins.BadRequestException
import com.larder.shared.api.EntryInput
import com.larder.shared.api.ExportFile
import com.larder.shared.api.PreviewInput
import com.larder.shared.api.TargetInput
import com.larder.shared.domain.validation
import com.larder.shared.service.EntryService
import com.larder.shared.service.HistoryService
import com.larder.shared.service.TargetService
import com.larder.shared.service.TransferService
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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
    suspend fun <T> ApplicationCall.entries(block: EntryService.() -> T) =
        withService(database, auth, { db, user, language -> EntryService(db, user, language = language) }, block)
    suspend fun <T> ApplicationCall.targets(block: TargetService.() -> T) = withService(database, auth, { db, user, _ -> TargetService(db, user) }, block)
    suspend fun <T> ApplicationCall.history(block: HistoryService.() -> T) = withService(database, auth, ::HistoryService, block)
    suspend fun <T> ApplicationCall.transfer(block: TransferService.() -> T) = withService(database, auth, { db, user, _ -> TransferService(db, user) }, block)

    get("/days/{date}") {
        val date = call.parameters["date"]!!
        call.respond(call.entries { day(date) })
    }

    route("/entries") {
        post {
            val input = call.receive<EntryInput>()
            call.respond(HttpStatusCode.Created, call.entries { create(input) })
        }
        post("/preview") {
            val input = call.receive<PreviewInput>()
            call.respond(call.entries { preview(input) })
        }
        get("/{id}") {
            val id = call.idParam()
            call.respond(call.entries { get(id) })
        }
        put("/{id}") {
            val id = call.idParam()
            val input = call.receive<EntryInput>()
            call.respond(call.entries { update(id, input) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.entries { delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    route("/targets") {
        get {
            call.respond(call.targets { list() })
        }
        post {
            val input = call.receive<TargetInput>()
            call.respond(call.targets { set(input) })
        }
        delete("/{id}") {
            val id = call.idParam()
            call.targets { delete(id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    get("/history") {
        val from = call.request.queryParameters["from"] ?: validation("'from' is required")
        val to = call.request.queryParameters["to"] ?: validation("'to' is required")
        call.respond(call.history { history(from, to) })
    }

    get("/history/contributions") {
        val from = call.request.queryParameters["from"] ?: validation("'from' is required")
        val to = call.request.queryParameters["to"] ?: validation("'to' is required")
        val nutrientId = call.request.queryParameters["nutrientId"]?.toLongOrNull() ?: validation("'nutrientId' is required")
        call.respond(call.history { contributions(from, to, nutrientId) })
    }

    get("/export") {
        val file = call.transfer { export() }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "larder-${LocalDate.now()}.json").toString(),
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
            throw AppException(ErrorCodes.INVALID_IMPORT, "Not a valid export file", 400)
        }
        call.respond(call.transfer { import(file, strategy) })
    }
}
