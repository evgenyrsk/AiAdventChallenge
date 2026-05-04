plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "com.example.aiadventchallenge"
version = "1.0"

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.example.fileassistant.FileAssistantMainKt")
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
}

kotlin {
    jvmToolchain(11)
}
