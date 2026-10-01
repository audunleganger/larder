package com.caloriecompanion.android.data

import com.caloriecompanion.shared.api.ConflictStrategy
import com.caloriecompanion.shared.api.DayView
import com.caloriecompanion.shared.api.EntryInput
import com.caloriecompanion.shared.api.EntryView
import com.caloriecompanion.shared.api.ErrorCodes
import com.caloriecompanion.shared.api.ErrorResponse
import com.caloriecompanion.shared.api.ExportFile
import com.caloriecompanion.shared.api.FoodDetail
import com.caloriecompanion.shared.api.FoodDto
import com.caloriecompanion.shared.api.FoodInput
import com.caloriecompanion.shared.api.FoodSummary
import com.caloriecompanion.shared.api.HealthResponse
import com.caloriecompanion.shared.api.HistoryView
import com.caloriecompanion.shared.api.ImportResult
import com.caloriecompanion.shared.api.LoginInput
import com.caloriecompanion.shared.api.LoginResult
import com.caloriecompanion.shared.api.NutrientDetail
import com.caloriecompanion.shared.api.NutrientDto
import com.caloriecompanion.shared.api.NutrientInput
import com.caloriecompanion.shared.api.NutrientOrderInput
import com.caloriecompanion.shared.api.PasswordChangeInput
import com.caloriecompanion.shared.api.PreviewInput
import com.caloriecompanion.shared.api.PreviewResult
import com.caloriecompanion.shared.api.TargetDto
import com.caloriecompanion.shared.api.TargetInput
import com.caloriecompanion.shared.api.UnitDetail
import com.caloriecompanion.shared.api.UnitDto
import com.caloriecompanion.shared.api.UnitInput
import com.caloriecompanion.shared.api.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

val apiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

/** Normalizes a user-typed server address: adds http:// when no scheme is given, drops trailing slashes. */
fun normalizeServerUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) trimmed else "http://$trimmed"
}

/** HTTP access to a Calorie Companion server. [token] is null before login. */
open class ServerClient(
    val baseUrl: String,
    @Volatile var token: String? = null,
    engine: HttpClientEngine? = null,
) {
    @PublishedApi
    internal val client: HttpClient = (if (engine != null) HttpClient(engine) { configure() } else HttpClient(OkHttp) { configure() })

    /** Called when the server rejects the session, so the app can ask for a new login. */
    var onUnauthorized: (() -> Unit)? = null

    private fun io.ktor.client.HttpClientConfig<*>.configure() {
        expectSuccess = false
        install(ContentNegotiation) { json(apiJson) }
    }

    @PublishedApi
    internal suspend fun send(method: HttpMethod, path: String, block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val response = try {
            client.request("$baseUrl$path") {
                this.method = method
                token?.let { bearerAuth(it) }
                block()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RepositoryException(RepositoryException.NETWORK, e.message ?: "Server unreachable")
        }
        if (response.status.isSuccess()) return response
        val error = runCatching { response.body<ErrorResponse>() }.getOrNull()
        if (error == null && response.status in setOf(HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout)) {
            throw RepositoryException(RepositoryException.NETWORK, "Server unreachable")
        }
        if (response.status == HttpStatusCode.Unauthorized && error?.error == ErrorCodes.UNAUTHORIZED) onUnauthorized?.invoke()
        throw RepositoryException(error?.error ?: "HTTP_${response.status.value}", error?.message ?: response.status.description, error?.details.orEmpty())
    }

    @PublishedApi
    internal suspend inline fun <reified T> call(method: HttpMethod, path: String, body: Any? = null, noinline block: HttpRequestBuilder.() -> Unit = {}): T {
        val response = send(method, path) {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            block()
        }
        return try {
            response.body<T>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RepositoryException("INVALID_RESPONSE", e.message ?: "Unexpected response from server")
        }
    }

    @PublishedApi
    internal suspend fun callUnit(method: HttpMethod, path: String, body: Any? = null) {
        send(method, path) {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    suspend fun health(): HealthResponse = call(HttpMethod.Get, "/api/health")
    suspend fun login(username: String, password: String): LoginResult =
        call(HttpMethod.Post, "/api/v1/auth/login", LoginInput(username, password))
    suspend fun logout() = callUnit(HttpMethod.Post, "/api/v1/auth/logout")
    suspend fun me(): UserDto = call(HttpMethod.Get, "/api/v1/me")
    suspend fun changePassword(current: String, new: String) =
        callUnit(HttpMethod.Put, "/api/v1/me/password", PasswordChangeInput(current, new))

    fun close() = client.close()
}

/** Server mode: the same operations as local mode, over the REST API. Online-only (O-1). */
class RemoteRepository(
    baseUrl: String,
    token: String?,
    engine: HttpClientEngine? = null,
) : ServerClient(baseUrl, token, engine), Repository {
    private val v1 = "/api/v1"

    override suspend fun units(includeArchived: Boolean): List<UnitDto> =
        call(HttpMethod.Get, "$v1/units") { parameter("includeArchived", includeArchived) }
    override suspend fun unitDetail(id: Long): UnitDetail = call(HttpMethod.Get, "$v1/units/$id")
    override suspend fun createUnit(input: UnitInput): UnitDto = call(HttpMethod.Post, "$v1/units", input)
    override suspend fun updateUnit(id: Long, input: UnitInput): UnitDto = call(HttpMethod.Put, "$v1/units/$id", input)
    override suspend fun archiveUnit(id: Long, archived: Boolean): UnitDto =
        call(HttpMethod.Post, "$v1/units/$id/${if (archived) "archive" else "unarchive"}")
    override suspend fun deleteUnit(id: Long) = callUnit(HttpMethod.Delete, "$v1/units/$id")

    override suspend fun nutrients(includeArchived: Boolean): List<NutrientDto> =
        call(HttpMethod.Get, "$v1/nutrients") { parameter("includeArchived", includeArchived) }
    override suspend fun nutrientDetail(id: Long): NutrientDetail = call(HttpMethod.Get, "$v1/nutrients/$id")
    override suspend fun createNutrient(input: NutrientInput): NutrientDto = call(HttpMethod.Post, "$v1/nutrients", input)
    override suspend fun updateNutrient(id: Long, input: NutrientInput): NutrientDto = call(HttpMethod.Put, "$v1/nutrients/$id", input)
    override suspend fun reorderNutrients(ids: List<Long>): List<NutrientDto> =
        call(HttpMethod.Put, "$v1/nutrients/order", NutrientOrderInput(ids))
    override suspend fun archiveNutrient(id: Long, archived: Boolean): NutrientDto =
        call(HttpMethod.Post, "$v1/nutrients/$id/${if (archived) "archive" else "unarchive"}")
    override suspend fun deleteNutrient(id: Long) = callUnit(HttpMethod.Delete, "$v1/nutrients/$id")

    override suspend fun foods(query: String?, includeArchived: Boolean): List<FoodSummary> =
        call(HttpMethod.Get, "$v1/foods") {
            query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
            parameter("includeArchived", includeArchived)
        }
    override suspend fun foodDetail(id: Long): FoodDetail = call(HttpMethod.Get, "$v1/foods/$id")
    override suspend fun createFood(input: FoodInput): FoodDto = call(HttpMethod.Post, "$v1/foods", input)
    override suspend fun updateFood(id: Long, input: FoodInput): FoodDto = call(HttpMethod.Put, "$v1/foods/$id", input)
    override suspend fun archiveFood(id: Long, archived: Boolean): FoodDto =
        call(HttpMethod.Post, "$v1/foods/$id/${if (archived) "archive" else "unarchive"}")
    override suspend fun deleteFood(id: Long) = callUnit(HttpMethod.Delete, "$v1/foods/$id")

    override suspend fun day(date: String): DayView = call(HttpMethod.Get, "$v1/days/$date")
    override suspend fun entry(id: Long): EntryView = call(HttpMethod.Get, "$v1/entries/$id")
    override suspend fun preview(input: PreviewInput): PreviewResult = call(HttpMethod.Post, "$v1/entries/preview", input)
    override suspend fun createEntry(input: EntryInput): EntryView = call(HttpMethod.Post, "$v1/entries", input)
    override suspend fun updateEntry(id: Long, input: EntryInput): EntryView = call(HttpMethod.Put, "$v1/entries/$id", input)
    override suspend fun deleteEntry(id: Long) = callUnit(HttpMethod.Delete, "$v1/entries/$id")

    override suspend fun targets(): List<TargetDto> = call(HttpMethod.Get, "$v1/targets")
    override suspend fun setTarget(input: TargetInput): TargetDto = call(HttpMethod.Post, "$v1/targets", input)
    override suspend fun deleteTarget(id: Long) = callUnit(HttpMethod.Delete, "$v1/targets/$id")

    override suspend fun history(from: String, to: String): HistoryView =
        call(HttpMethod.Get, "$v1/history") {
            parameter("from", from)
            parameter("to", to)
        }

    override suspend fun export(): ExportFile = call(HttpMethod.Get, "$v1/export")
    override suspend fun import(file: ExportFile, strategy: ConflictStrategy): ImportResult =
        call(HttpMethod.Post, "$v1/import", file) {
            parameter("onConflict", if (strategy == ConflictStrategy.OVERWRITE) "overwrite" else "skip")
        }
}
