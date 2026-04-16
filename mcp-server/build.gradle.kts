plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.example.aiadventchallenge"
version = "1.0"

dependencies {
    implementation(project(":rag-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.pdfbox:pdfbox:2.0.30")

    testImplementation(kotlin("test"))
}

tasks.register("run", JavaExec::class) {
    group = "application"
    description = "Run MCP Server (single server on port 8080)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.MainKt")
}

tasks.register("runMultiServer", JavaExec::class) {
    group = "application"
    description = "Run all 3 MCP servers (ports 8081, 8082, 8083)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.MultiServerLauncherKt")
}

tasks.register("runNutritionServer", JavaExec::class) {
    group = "application"
    description = "Run Nutrition Metrics Server (port 8081)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.servers.NutritionMetricsServerKt")
}

tasks.register("runMealServer", JavaExec::class) {
    group = "application"
    description = "Run Meal Guidance Server (port 8082)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.servers.MealGuidanceServerKt")
}

tasks.register("runTrainingServer", JavaExec::class) {
    group = "application"
    description = "Run Training Guidance Server (port 8083)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.servers.TrainingGuidanceServerKt")
}

tasks.register("runDocumentIndexServer", JavaExec::class) {
    group = "application"
    description = "Run Document Index Server (port 8084)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.servers.DocumentIndexServerMainKt")
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

tasks.register("runFitnessRagEvaluation", JavaExec::class) {
    group = "application"
    description = "Run plain LLM vs RAG evaluation for the fitness knowledge base"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.mcp.server.evaluation.FitnessRagEvaluationRunnerKt")
}
