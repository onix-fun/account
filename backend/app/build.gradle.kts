plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":infrastructure"))
    implementation(project(":application"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.swagger.ui)
    implementation(libs.grpc.netty)
    implementation(libs.grpc.services)
    implementation(libs.java.jwt)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    testImplementation(project(":domain"))
    testImplementation(libs.protobuf.java)
    testImplementation(libs.grpc.stub)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.shadowJar {
    archiveClassifier.set("with-dependencies")
    mergeServiceFiles()
    manifest { attributes("Main-Class" to "com.onix.account.app.MainKt") }
}

tasks.test {
    maxParallelForks = 1
    environment("DOCKER_API_VERSION", System.getenv("DOCKER_API_VERSION") ?: "1.44")
    systemProperty("api.version", System.getenv("DOCKER_API_VERSION") ?: "1.44")
    systemProperty("kotlinx.coroutines.test.default_timeout", "5m")
}

tasks.build { dependsOn(tasks.shadowJar) }
