package com.caloriecompanion.server

import com.caloriecompanion.shared.AppInfo
import java.nio.file.Path
import kotlin.io.path.Path

/** Server configuration, read from environment variables (DEP-2). */
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val dataDir: Path = Path("data"),
    /** Directory with the built web GUI; when null the server only serves the API. */
    val webDir: Path? = null,
    val tokenLifetimeDays: Long = 30,
    val bcryptCost: Int = 12,
    /** Reported by `/api/health` (DEP-5). The Docker image sets it to the release or commit it was built from. */
    val version: String = AppInfo.VERSION,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            host = env["CC_HOST"] ?: "0.0.0.0",
            port = env["CC_PORT"]?.toInt() ?: 8080,
            dataDir = Path(env["CC_DATA_DIR"] ?: "data"),
            webDir = env["CC_WEB_DIR"]?.let(::Path),
            tokenLifetimeDays = env["CC_TOKEN_LIFETIME_DAYS"]?.toLong() ?: 30,
            version = env["CC_VERSION"]?.takeIf { it.isNotBlank() } ?: AppInfo.VERSION,
        )
    }
}
