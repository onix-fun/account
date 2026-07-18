import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

val protobufVersion = "3.25.5"
val grpcVersion = "1.68.1"

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.swagger.ui)

    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty)
    implementation(libs.grpc.services)
    implementation(libs.protobuf.java)
    implementation(libs.javax.annotation.api)

    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.lettuce.core)
    implementation(libs.argon2.jvm)
    implementation(libs.java.jwt)
    implementation(libs.jwks.rsa)
    implementation(libs.angus.mail)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.uuid.generator)
    implementation(libs.opentelemetry.api)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

sourceSets.main {
    proto.srcDir("../../api/proto")
    proto.srcDir("../../.contracts/profile/proto")
    proto.srcDir("../../.contracts/media/proto")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins { id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" } }
    generateProtoTasks { all().configureEach { plugins { id("grpc") } } }
}
