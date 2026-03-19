plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    application
}

repositories {
    mavenCentral()
}

group = "lab.gabon"
version = "0.1.0"

application {
    mainClass.set("lab.gabon.ApplicationKt")
    applicationDefaultJvmArgs =
        listOf(
            "-XX:+UseZGC",
            "-XX:+ZGenerational",
            "-Xmx512m",
            "-Xms256m",
        )
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    // Flyway
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Auth
    implementation(libs.bcrypt)

    // Redis
    implementation(libs.lettuce)

    // S3
    implementation(libs.aws.s3)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactive)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.shadowJar {
    archiveFileName.set("gabon-api.jar")
    mergeServiceFiles()
}

ktlint {
    version.set("1.6.0")
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("detekt.yml"))
}
