package com.caloriecompanion.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.caloriecompanion.android.data.AppSettings
import com.caloriecompanion.android.data.LocalRepository
import com.caloriecompanion.android.data.Mode
import com.caloriecompanion.android.data.RemoteRepository
import com.caloriecompanion.android.data.Repository
import com.caloriecompanion.android.data.RepositoryException
import com.caloriecompanion.android.data.ServerClient
import com.caloriecompanion.android.data.normalizeServerUrl
import com.caloriecompanion.shared.AppInfo

/** App-wide state: the active mode and repository, and a version counter that makes screens reload. */
class AppState(private val context: Context) {
    val settings = AppSettings(context)
    private var local: LocalRepository? = null

    var repository: Repository? by mutableStateOf(null)
        private set

    /** Set when a server session expired; the setup screen then asks for a new login. */
    var sessionExpired by mutableStateOf(false)
        private set

    /** Incremented after every change, so every loaded screen refreshes (edits change totals everywhere, E-4). */
    var dataVersion by mutableIntStateOf(0)
        private set

    val mode: Mode? get() = settings.mode
    val remote: RemoteRepository? get() = repository as? RemoteRepository

    init {
        when (settings.mode) {
            Mode.LOCAL -> repository = localRepository()
            Mode.SERVER -> {
                val url = settings.serverUrl
                val token = settings.token
                if (url != null && token != null) repository = remoteRepository(url, token)
            }
            null -> Unit
        }
    }

    fun changed() {
        dataVersion++
    }

    fun useLocal() {
        settings.mode = Mode.LOCAL
        repository = localRepository()
        changed()
    }

    /** Checks the server, logs in and switches to server mode. Throws [RepositoryException] on failure. */
    suspend fun connect(url: String, username: String, password: String) {
        val baseUrl = normalizeServerUrl(url)
        val client = ServerClient(baseUrl)
        try {
            val health = client.health()
            if (health.apiVersion != AppInfo.API_VERSION) {
                throw RepositoryException(INCOMPATIBLE, "Server API version ${health.apiVersion}")
            }
            val login = client.login(username, password)
            settings.mode = Mode.SERVER
            settings.serverUrl = baseUrl
            settings.token = login.token
            settings.username = login.user.username
            sessionExpired = false
            repository = remoteRepository(baseUrl, login.token)
            changed()
        } finally {
            client.close()
        }
    }

    /** Leaves the current mode and returns to the setup screen. Local data stays on the device. */
    suspend fun leaveMode() {
        remote?.let { runCatching { it.logout() }; it.close() }
        settings.mode = null
        settings.token = null
        repository = null
        sessionExpired = false
    }

    private fun localRepository(): LocalRepository = local ?: LocalRepository.open(context).also { local = it }

    private fun remoteRepository(url: String, token: String) = RemoteRepository(url, token).also { repo ->
        repo.onUnauthorized = {
            settings.token = null
            repository = null
            sessionExpired = true
        }
    }

    companion object {
        const val INCOMPATIBLE = "INCOMPATIBLE"
    }
}
