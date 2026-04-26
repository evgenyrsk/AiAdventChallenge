plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "com.example.aiadventchallenge"
version = "1.0"

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

application {
    mainClass.set("com.example.privateaiservice.PrivateAiServiceMainKt")
}

tasks.register<JavaExec>("runSmoke") {
    group = "verification"
    description = "Run sequential smoke check against the private AI service."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.privateaiservice.PrivateAiServiceSmokeKt")
}
