plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.example.aiadventchallenge"
version = "1.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
}

tasks.register("run", JavaExec::class) {
    group = "application"
    description = "Run the MCP Server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.MainKt")
}
