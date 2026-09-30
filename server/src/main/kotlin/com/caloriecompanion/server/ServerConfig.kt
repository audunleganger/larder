package com.caloriecompanion.server

import java.nio.file.Path
import kotlin.io.path.Path

/** Server configuration, read from environment variables (DEP-2). */
data class ServerConfig(
    val host: String,
    val port: Int,
    val dataDir: Path,
    /** Directory with the built web GUI; when null the server only serves the API. */
    val webDir: Path?,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            host = env["CC_HOST"] ?: "0.0.0.0",
            port = env["CC_PORT"]?.toInt() ?: 8080,
            dataDir = Path(env["CC_DATA_DIR"] ?: "data"),
            webDir = env["CC_WEB_DIR"]?.let(::Path),
        )
    }
}
