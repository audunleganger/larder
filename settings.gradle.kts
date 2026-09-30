import java.util.Properties

rootProject.name = "calorie-companion"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads the JDK toolchain (21) on demand, so the build doesn't depend on the system JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared", ":server")

// The Android app is only included when an Android SDK is configured,
// so the server and shared modules build on machines without one.
val localProperties = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val androidSdkConfigured = localProperties.getProperty("sdk.dir") != null ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (androidSdkConfigured) {
    include(":android")
} else {
    logger.lifecycle("No Android SDK configured (sdk.dir / ANDROID_HOME); skipping :android")
}
