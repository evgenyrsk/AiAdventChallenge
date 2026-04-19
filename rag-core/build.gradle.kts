plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.example.aiadventchallenge"
version = "1.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(11)
}
