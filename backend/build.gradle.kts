plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.shadow)
}

group = "profile"
version = "1.0.0-SNAPSHOT"

val protobufVersion = "3.25.5"
val grpcVersion = "1.68.1"

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Ktor Server
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

    // gRPC
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty)
    implementation(libs.grpc.services)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.kotlin)
    implementation(libs.javax.annotation.api)

    // Database
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Redis
    implementation(libs.lettuce.core)

    // Security
    implementation(libs.argon2.jvm)
    implementation(libs.java.jwt)
    implementation(libs.jwks.rsa)

    // Storage
    implementation(libs.minio)

    // Logging
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.angus.mail)

    // DI
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // UUID v7
    implementation(libs.uuid.generator)

    // OTel
    implementation(libs.opentelemetry.api)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
            }
        }
    }
}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("with-dependencies")
    mergeServiceFiles()
    manifest {
        attributes("Main-Class" to "bootstrap.MainKt")
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
