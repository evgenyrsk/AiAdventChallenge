plugins {
    kotlin("jvm")
}

group = "com.example.aiadventchallenge"
version = "1.0"

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(11)
}
