package com.caloriecompanion.server

import at.favre.lib.crypto.bcrypt.BCrypt
import com.caloriecompanion.db.App_user
import com.caloriecompanion.db.CalorieCompanionDatabase
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.UserDto
import com.caloriecompanion.shared.domain.AppException
import com.caloriecompanion.shared.service.Seeder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

data class UserPrincipal(val id: Long, val username: String, val isAdmin: Boolean, val locale: String? = null)

fun App_user.toDto() = UserDto(id, username, is_admin, is_disabled, locale, created_at)

/** Users, passwords and sessions (A-1 … A-4). All methods run on the database thread. */
class Auth(private val config: ServerConfig, private val now: () -> Long = System::currentTimeMillis) {
    private val random = SecureRandom()
    private val lifetimeMillis = TimeUnit.DAYS.toMillis(config.tokenLifetimeDays)

    fun hashPassword(password: String): String {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw AppException(ErrorCodes.WEAK_PASSWORD, "Password must be at least $MIN_PASSWORD_LENGTH characters")
        }
        // bcrypt only uses the first 72 bytes; longer (e.g. multi-byte) passwords are rejected up front.
        if (password.toByteArray().size > MAX_PASSWORD_BYTES) {
            throw AppException(ErrorCodes.WEAK_PASSWORD, "Password is too long (at most $MAX_PASSWORD_BYTES bytes)")
        }
        return BCrypt.withDefaults().hashToString(config.bcryptCost, password.toCharArray())
    }

    fun verifyPassword(password: String, hash: String?): Boolean =
        hash != null && password.toByteArray().size <= MAX_PASSWORD_BYTES && BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    /** Creates a user and seeds their catalog. */
    fun createUser(db: CalorieCompanionDatabase, username: String, password: String, isAdmin: Boolean, locale: String?): App_user =
        db.transactionWithResult {
            val name = cleanUsername(username)
            if (db.appUserQueries.selectByUsername(name).executeAsOneOrNull() != null) {
                throw AppException(ErrorCodes.NAME_TAKEN, "Username '$name' is taken", 409)
            }
            db.appUserQueries.insertUser(name, hashPassword(password), isAdmin, cleanLocale(locale), now())
            val id = db.appUserQueries.lastInsertRowId().executeAsOne()
            Seeder.seed(db, id)
            db.appUserQueries.selectById(id).executeAsOne()
        }

    /** Returns the user for valid credentials; throws INVALID_CREDENTIALS otherwise. */
    fun login(db: CalorieCompanionDatabase, username: String, password: String): App_user {
        val user = db.appUserQueries.selectByUsername(username.trim()).executeAsOneOrNull()
        if (user == null || user.is_disabled || !verifyPassword(password, user.password_hash)) {
            throw AppException(ErrorCodes.INVALID_CREDENTIALS, "Wrong username or password", 401)
        }
        return user
    }

    /** Starts a session and returns the bearer token. Only its hash is stored. */
    fun createSession(db: CalorieCompanionDatabase, userId: Long): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val time = now()
        db.sessionQueries.deleteExpired(time)
        db.sessionQueries.insertSession(hashToken(token), userId, time, time + lifetimeMillis)
        return token
    }

    /** Resolves a bearer token, extending the session's expiry (sliding, at most once an hour). */
    fun authenticate(db: CalorieCompanionDatabase, token: String): UserPrincipal? {
        val hash = hashToken(token)
        val session = db.sessionQueries.selectSession(hash).executeAsOneOrNull() ?: return null
        val time = now()
        if (session.expires_at < time) {
            db.sessionQueries.deleteSession(hash)
            return null
        }
        val user = db.appUserQueries.selectById(session.user_id).executeAsOneOrNull() ?: return null
        if (user.is_disabled) return null
        if (session.expires_at - time < lifetimeMillis - TimeUnit.HOURS.toMillis(1)) {
            db.sessionQueries.extendSession(time + lifetimeMillis, hash)
        }
        return UserPrincipal(user.id, user.username, user.is_admin, user.locale)
    }

    fun logout(db: CalorieCompanionDatabase, token: String) = db.sessionQueries.deleteSession(hashToken(token))

    fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_BYTES = 72

        fun cleanUsername(username: String): String {
            val name = username.trim()
            if (name.isEmpty()) throw AppException(ErrorCodes.NAME_REQUIRED, "Username is required")
            if (name.length > 50) throw AppException(ErrorCodes.VALIDATION, "Username is longer than 50 characters")
            if (name.any { it.isWhitespace() }) throw AppException(ErrorCodes.VALIDATION, "Username can't contain spaces")
            return name
        }

        fun cleanLocale(locale: String?): String? = when {
            locale == null -> null
            Seeder.isNorwegian(locale) -> "nb"
            else -> "en"
        }
    }
}
