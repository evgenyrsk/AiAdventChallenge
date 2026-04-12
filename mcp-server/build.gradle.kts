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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.register("run", JavaExec::class) {
    group = "application"
    description = "Run the MCP Server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.MainKt")
}

tasks.register("runDemo", JavaExec::class) {
    group = "application"
    description = "Run Fitness Summary Export Demo"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.demo.DemoFitnessSummaryExportKt")
}

tasks.register("runMultiMcpDemo", JavaExec::class) {
    group = "application"
    description = "Run Multi-MCP Orchestration Demo"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.demo.MultiMcpDemoKt")
}

tasks.register<JavaExec>("setupTestData") {
    group = "application"
    description = "Set up test fitness data for testing pipeline (supports 7 or 30 days)"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.demo.SetupTestDataKt")

    val period = project.findProperty("period")?.toString() ?: "7"
    args = listOf(period)

    doFirst {
        println("📅 Setting up test data for $period days...")
    }
}

tasks.register("runFitnessFlowDebug", JavaExec::class) {
    group = "application"
    description = "Run Fitness Flow Debug Script"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.debug.FitnessFlowDebugKt")
}
