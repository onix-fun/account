package profile

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import profile.auth.AuthController
import profile.auth.LoginRequest
import profile.auth.RegisterRequest
import profile.auth.authRouting
import profile.infrastructure.di.koinModule
import profile.infrastructure.events.EmailEventConsumer
import profile.search.SearchController
import profile.search.SearchService
import profile.search.searchRouting
import profile.sessions.SessionController
import profile.sessions.sessionRouting
import profile.users.UserController
import profile.users.userRouting

fun Application.module() {
    // 1. Configure Plugins
    install(Koin) {
        slf4jLogger()
        modules(koinModule(environment.config))
    }

    install(ContentNegotiation) {
        json()
    }

    install(RequestValidation) {
        validate<RegisterRequest> { request ->
            when {
                request.email.isBlank() -> ValidationResult.Invalid("Email cannot be blank")
                !request.email.contains("@") -> ValidationResult.Invalid("Invalid email format")
                request.password.length < 8 -> ValidationResult.Invalid("Password must be at least 8 characters")
                else -> ValidationResult.Valid
            }
        }
        validate<LoginRequest> { request ->
            when {
                request.email.isBlank() -> ValidationResult.Invalid("Email cannot be blank")
                request.password.isBlank() -> ValidationResult.Invalid("Password cannot be blank")
                else -> ValidationResult.Valid
            }
        }
    }

    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.reasons.joinToString()))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
        }
        exception<IllegalStateException> { call, cause ->
            call.application.log.error("Illegal state: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to cause.message))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Internal server error: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-User-Id")
        
        anyHost()
        
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }

    install(SwaggerUI) {
        info {
            title = "Identity Service API"
            version = "1.0.0"
            description = "User identity management API"
        }
    }
    
    // 2. Inject Services
    val searchService by inject<SearchService>()
    val emailEventConsumer by inject<EmailEventConsumer>()
    val authController by inject<AuthController>()
    val userController by inject<UserController>()
    val searchController by inject<SearchController>()
    val sessionController by inject<SessionController>()
    
    // 3. Background Tasks
    launch {
        try {
            searchService.indexAllUsers()
        } catch (e: Exception) {
            log.error("Failed to index users: ${e.message}")
        }
    }

    launch {
        try {
            emailEventConsumer.start()
        } catch (e: Exception) {
            log.error("Failed to start email event consumer: ${e.message}")
        }
    }
    
    // 4. Configure Routing
    routing {
        get("health", {
            summary = "Health check"
            description = "Returns API health status"
            response { code(HttpStatusCode.OK) { description = "API is healthy" } }
        }) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
        }
        
        route("openapi.json") { openApiSpec("api") }
        route("swagger-ui") { swaggerUI("/openapi.json") }
        
        authRouting(authController, sessionController)
        userRouting(userController)
        searchRouting(searchController)
        sessionRouting(sessionController)
    }
}
