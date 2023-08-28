plugins {
    application
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.4.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:5.6.1")
    implementation("io.javalin.community.ssl:ssl-plugin:5.6.1")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("io.arrow-kt:arrow-core:1.2.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}