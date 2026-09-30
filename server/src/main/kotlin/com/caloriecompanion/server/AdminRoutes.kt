package com.caloriecompanion.server

import com.caloriecompanion.shared.api.AdminUserCreate
import com.caloriecompanion.shared.api.AdminUserUpdate
import com.caloriecompanion.shared.api.BackupResult
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.notFound
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** User management (A-3) and backups (DEP-4). */
fun Route.adminRoutes(database: Database, auth: Auth) = route("/admin") {
    get("/users") {
        call.admin(database, auth)
        call.respond(database.run { db -> db.appUserQueries.selectAll().executeAsList().map { it.toDto() } })
    }

    post("/users") {
        call.admin(database, auth)
        val input = call.receive<AdminUserCreate>()
        val user = database.run { auth.createUser(it, input.username, input.password, input.isAdmin, input.locale) }
        call.respond(HttpStatusCode.Created, user.toDto())
    }

    patch("/users/{id}") {
        call.admin(database, auth)
        val id = call.idParam()
        val input = call.receive<AdminUserUpdate>()
        val user = database.run { db ->
            db.transactionWithResult {
                val user = db.appUserQueries.selectById(id).executeAsOneOrNull() ?: notFound("User")
                val losesAdmin = user.is_admin && !user.is_disabled && (input.isAdmin == false || input.isDisabled == true)
                if (losesAdmin && db.appUserQueries.countActiveAdmins().executeAsOne() <= 1) {
                    throw AppException(ErrorCodes.LAST_ADMIN, "There must be at least one active admin", 409)
                }
                input.isAdmin?.let { db.appUserQueries.updateAdmin(it, id) }
                input.isDisabled?.let {
                    db.appUserQueries.updateDisabled(it, id)
                    if (it) db.sessionQueries.deleteSessionsForUser(id)
                }
                input.password?.let {
                    db.appUserQueries.updatePassword(auth.hashPassword(it), id)
                    db.sessionQueries.deleteSessionsForUser(id)
                }
                db.appUserQueries.selectById(id).executeAsOne()
            }
        }
        call.respond(user.toDto())
    }

    post("/backup") {
        call.admin(database, auth)
        val path = database.backup()
        call.respond(BackupResult(path.fileName.toString(), database.sizeOf(path)))
    }
}
