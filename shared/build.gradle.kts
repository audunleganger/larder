import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

// Pure-Kotlin JVM library consumed by both :server and :android.
// Bytecode targets Java 17 so Android can consume it.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

sqldelight {
    databases {
        create("CalorieCompanionDatabase") {
            packageName.set("com.caloriecompanion.db")
        }
    }
}

dependencies {
    api(libs.sqldelight.runtime)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.sqldelight.sqlite.driver)
}

tasks.test {
    useJUnitPlatform()
    // TypeScriptTypesTest writes web/src/api/types.gen.ts when -PupdateTsTypes=true.
    val updateTsTypes = providers.gradleProperty("updateTsTypes").orElse("false")
    inputs.property("updateTsTypes", updateTsTypes)
    inputs.files(rootProject.file("web/src/api/types.gen.ts")).withPropertyName("tsTypes")
    systemProperty("updateTsTypes", updateTsTypes.get())
}
