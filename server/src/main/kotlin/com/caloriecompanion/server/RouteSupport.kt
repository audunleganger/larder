package com.caloriecompanion.server

import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.domain.Languages
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/** The bearer token of the request, if any. */
fun ApplicationCall.bearerToken(): String? =
    request.header(HttpHeaders.Authorization)
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring(7)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** The authenticated user; fails with 401 otherwise. */
suspend fun ApplicationCall.user(database: Database, auth: Auth): UserPrincipal {
    val token = bearerToken() ?: throw AppException(ErrorCodes.UNAUTHORIZED, "Login required", 401)
    return database.run { auth.authenticate(it, token) }
        ?: throw AppException(ErrorCodes.UNAUTHORIZED, "Session expired or invalid", 401)
}

/** The authenticated admin; fails with 401/403 otherwise. */
suspend fun ApplicationCall.admin(database: Database, auth: Auth): UserPrincipal {
    val user = user(database, auth)
    if (!user.isAdmin) throw AppException(ErrorCodes.FORBIDDEN, "Admin only", 403)
    return user
}

/** Runs [block] on the database thread with the authenticated user's id. */
suspend fun <T> ApplicationCall.withUser(database: Database, auth: Auth, block: (CalorieCompanionDatabase, Long) -> T): T {
    val user = user(database, auth)
    return database.run { block(it, user.id) }
}

/**
 * The reader's language for display names (L-5): the request's Accept-Language (the web app sends
 * the language it shows), otherwise the user's saved language.
 */
fun ApplicationCall.language(user: UserPrincipal): String? =
    Languages.of(request.header(HttpHeaders.AcceptLanguage)) ?: Languages.of(user.locale)

/** Like [withUser], also passing the reader's [language]. */
suspend fun <T> ApplicationCall.withUserLanguage(
    database: Database,
    auth: Auth,
    block: (CalorieCompanionDatabase, Long, String?) -> T,
): T {
    val user = user(database, auth)
    val language = language(user)
    return database.run { block(it, user.id, language) }
}

fun ApplicationCall.idParam(name: String = "id"): Long =
    parameters[name]?.toLongOrNull() ?: throw AppException(ErrorCodes.NOT_FOUND, "Not found", 404)

fun ApplicationCall.flag(name: String): Boolean = request.queryParameters[name]?.lowercase() in setOf("true", "1", "yes")
