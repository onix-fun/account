import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.shadow) apply false
}

allprojects {
    group = "com.onix.account"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(21) }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}

tasks.register("checkModuleBoundaries") {
    group = "verification"
    doLast {
        val allowed = mapOf(
            ":domain" to emptySet(),
            ":application" to setOf(":domain"),
            ":infrastructure" to setOf(":domain", ":application"),
            ":app" to setOf(":application", ":infrastructure"),
        )
        subprojects.forEach { project ->
            val dependencies = project.configurations
                .filter { it.name in setOf("api", "implementation") }
                .flatMap { it.dependencies.withType(org.gradle.api.artifacts.ProjectDependency::class.java) }
                .map { it.path }
                .toSet()
            check(dependencies.all(allowed.getValue(project.path)::contains)) {
                "${project.path} has forbidden project dependencies: ${dependencies - allowed.getValue(project.path)}"
            }
        }
    }
}
