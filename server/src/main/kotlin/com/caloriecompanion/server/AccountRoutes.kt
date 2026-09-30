package com.caloriecompanion.server

import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.LocaleInput
import com.caloriecompanion.shared.api.LoginInput
import com.caloriecompanion.shared.api.LoginResult
import com.caloriecompanion.shared.api.PasswordChangeInput
import com.caloriecompanion.shared.api.SetupInput
import com.caloriecompanion.shared.api.SetupStatus
import com.caloriecompanion.shared.domain.AppException
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.coroutines.delay

/** First-run setup (A-2), login/logout and the user's own account (A-4). */
fun Route.accountRoutes(database: Database, auth: Auth) {
    get("/setup") {
        call.respond(SetupStatus(needsSetup = database.run { it.appUserQueries.countUsers().executeAsOne() == 0L }))
    }

    post("/setup") {
        val input = call.receive<SetupInput>()
        val result = database.run { db ->
            db.transactionWithResult {
                if (db.appUserQueries.countUsers().executeAsOne() > 0) {
                    throw AppException(ErrorCodes.SETUP_DONE, "Setup has already been completed", 409)
                }
                val user = auth.createUser(db, input.username, input.password, isAdmin = true, locale = input.locale)
                LoginResult(auth.createSession(db, user.id), user.toDto())
            }
        }
        call.respond(HttpStatusCode.Created, result)
    }

    post("/auth/login") {
        val input = call.receive<LoginInput>()
        val result = try {
            database.run { db ->
                val user = auth.login(db, input.username, input.password)
                LoginResult(auth.createSession(db, user.id), user.toDto())
            }
        } catch (e: AppException) {
            delay(500) // slow down password guessing
            throw e
        }
        call.respond(result)
    }

    post("/auth/logout") {
        call.bearerToken()?.let { token -> database.run { auth.logout(it, token) } }
        call.respond(HttpStatusCode.NoContent)
    }

    get("/me") {
        val user = call.user(database, auth)
        call.respond(database.run { it.appUserQueries.selectById(user.id).executeAsOne().toDto() })
    }

    put("/me/password") {
        val user = call.user(database, auth)
        val input = call.receive<PasswordChangeInput>()
        val token = call.bearerToken()!!
        database.run { db ->
            val row = db.appUserQueries.selectById(user.id).executeAsOne()
            if (!auth.verifyPassword(input.currentPassword, row.password_hash)) {
                throw AppException(ErrorCodes.INVALID_CREDENTIALS, "Current password is wrong", 400)
            }
            db.transaction {
                db.appUserQueries.updatePassword(auth.hashPassword(input.newPassword), user.id)
                db.sessionQueries.deleteSessionsForUserExcept(user.id, auth.hashToken(token))
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }

    put("/me/locale") {
        val user = call.user(database, auth)
        val input = call.receive<LocaleInput>()
        call.respond(database.run { db ->
            db.appUserQueries.updateLocale(Auth.cleanLocale(input.locale), user.id)
            db.appUserQueries.selectById(user.id).executeAsOne().toDto()
        })
    }
}
