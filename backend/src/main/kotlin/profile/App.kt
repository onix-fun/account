package profile

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthScheme
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.http.*
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import profile.auth.AuthController
import profile.auth.ChangePasswordRequest
import profile.auth.ConfirmRegistrationRequest
import profile.auth.DeleteAccountRequest
import profile.auth.ForgotPasswordRequest
import profile.auth.LoginRequest
import profile.auth.RegisterRequest
import profile.auth.ResendRegistrationCodeRequest
import profile.auth.ResetPasswordRequest
import profile.auth.PublicVerificationConfirmRequest
import profile.auth.PublicVerificationRequest
import profile.auth.authRouting
import profile.grpc.ProfileGrpcServer
import profile.infrastructure.db.UserRepository
import profile.infrastructure.di.koinModule
import profile.infrastructure.events.EmailEventConsumer
import profile.infrastructure.jwt.RsaKeyLoader
import profile.search.SearchController
import profile.search.SearchService
import profile.search.searchRouting
import profile.sessions.SessionController
import profile.sessions.sessionRouting
import profile.shared.ApiErrorCode
import profile.shared.ApiErrorResponse
import profile.shared.ApiException
import profile.shared.ApiFieldError
import profile.users.UserController
import profile.users.RequestEmailChangeRequest
import profile.users.ConfirmEmailChangeRequest
import profile.infrastructure.db.SessionRepository
import profile.infrastructure.config.SecurityConfig
import profile.infrastructure.ratelimit.RateLimit
import profile.infrastructure.ratelimit.RateLimitConfig
import profile.infrastructure.redis.RedisManager
import profile.users.userRouting

private const val MAX_BODY_SIZE = 5L * 1024 * 1024 // 5MB, matches AVATAR_TOO_LARGE

fun Application.module() {
    // 1. Configure Plugins
    install(Koin) {
        slf4jLogger()
        modules(koinModule(environment.config))
    }

    install(ContentNegotiation) {
        json(Json {
            explicitNulls = false
            encodeDefaults = true
        })
    }

    intercept(ApplicationCallPipeline.Setup) {
        val contentLength = context.request.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_BODY_SIZE) {
            context.respond(HttpStatusCode.PayloadTooLarge, ApiErrorResponse(
                code = ApiErrorCode.AVATAR_TOO_LARGE.name,
                numericCode = ApiErrorCode.AVATAR_TOO_LARGE.numericCode,
                message = ApiErrorCode.AVATAR_TOO_LARGE.message,
                fieldErrors = emptyList()
            ))
            return@intercept
        }
    }

    install(RequestValidation) {
        validate<RegisterRequest> { request ->
            when {
                request.email.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "email")
                !request.email.contains("@") -> invalid(ApiErrorCode.VALIDATION_INVALID_EMAIL, "email")
                request.username.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "username")
                request.username.length < 3 -> invalid(ApiErrorCode.VALIDATION_USERNAME_TOO_SHORT, "username")
                request.password.length < 8 -> invalid(ApiErrorCode.VALIDATION_PASSWORD_TOO_SHORT, "password")
                else -> ValidationResult.Valid
            }
        }
        validate<ConfirmRegistrationRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            when {
                identifier.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                !request.code.matches(Regex("\\d{6}")) -> invalid(ApiErrorCode.VALIDATION_INVALID_CODE, "code")
                else -> ValidationResult.Valid
            }
        }
        validate<ResendRegistrationCodeRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            when {
                identifier.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                else -> ValidationResult.Valid
            }
        }
        validate<LoginRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            when {
                identifier.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                request.password.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "password")
                else -> ValidationResult.Valid
            }
        }
        validate<ForgotPasswordRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            when {
                identifier.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                else -> ValidationResult.Valid
            }
        }
        validate<ResetPasswordRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            val code = request.code ?: request.token.orEmpty()
            when {
                identifier.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                !code.matches(Regex("\\d{6}")) -> invalid(ApiErrorCode.VALIDATION_INVALID_CODE, "code")
                request.newPassword.length < 8 -> invalid(ApiErrorCode.VALIDATION_PASSWORD_TOO_SHORT, "newPassword")
                else -> ValidationResult.Valid
            }
        }
        validate<ChangePasswordRequest> { request ->
            when {
                request.currentPassword.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "currentPassword")
                request.newPassword.length < 8 -> invalid(ApiErrorCode.VALIDATION_PASSWORD_TOO_SHORT, "newPassword")
                else -> ValidationResult.Valid
            }
        }
        validate<DeleteAccountRequest> { request ->
            when {
                request.password.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "password")
                else -> ValidationResult.Valid
            }
        }
        validate<PublicVerificationRequest> { request ->
            if (request.identifier.isBlank()) invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
            else ValidationResult.Valid
        }
        validate<PublicVerificationConfirmRequest> { request ->
            when {
                request.identifier.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                !request.code.matches(Regex("\\d{6}")) -> invalid(ApiErrorCode.VALIDATION_INVALID_CODE, "code")
                else -> ValidationResult.Valid
            }
        }
        validate<RequestEmailChangeRequest> { request ->
            when {
                request.currentPassword.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "currentPassword")
                request.newEmail.isBlank() -> invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "newEmail")
                else -> ValidationResult.Valid
            }
        }
        validate<ConfirmEmailChangeRequest> { request ->
            if (!request.code.matches(Regex("\\d{6}"))) invalid(ApiErrorCode.VALIDATION_INVALID_CODE, "code") else ValidationResult.Valid
        }
    }

    install(RateLimit) {
        route("/api/auth/login", max = 20, windowSeconds = 60)
        route("/api/auth/token", max = 20, windowSeconds = 60)
        route("/api/auth/register", max = 20, windowSeconds = 60)
        route("/api/auth/resend-registration-code", max = 20, windowSeconds = 60)
        route("/api/auth/forgot-password", max = 20, windowSeconds = 60)
        route("/api/auth/reset-password", max = 20, windowSeconds = 60)
        route("/api/auth/confirm-registration", max = 20, windowSeconds = 60)
        route("/api/auth/verify-email", max = 20, windowSeconds = 60)
        route("/api/auth/username-available", max = 20, windowSeconds = 60)
        route("/api/auth/account-lookup", max = 20, windowSeconds = 60)
        route("/api/auth/public-verification/", max = 20, windowSeconds = 60)
        route("/api/auth/token/refresh", max = 30, windowSeconds = 60)
        route("/api/auth/refresh", max = 30, windowSeconds = 60)
        route("/api/auth/switch", max = 30, windowSeconds = 60)
        route("/api/auth/logout", max = 30, windowSeconds = 60)
        route("/api/users/me/avatar", max = 20, windowSeconds = 60)
    }

    val redisManager by inject<RedisManager>()
    environment.monitor.subscribe(ApplicationStopping) {
        redisManager.close()
    }

    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            val parts = cause.reasons.firstOrNull()?.split("|", limit = 2).orEmpty()
            val error = parts.firstOrNull()?.let { runCatching { ApiErrorCode.valueOf(it) }.getOrNull() }
                ?: ApiErrorCode.VALIDATION_INVALID_REQUEST
            val fields = parts.getOrNull(1)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            call.respondApiError(error, fields)
        }
        exception<ApiException> { call, cause ->
            call.respondApiError(cause.error, cause.fields)
        }
        exception<IllegalStateException> { call, cause ->
            call.application.log.error("Illegal state: ${cause.message}", cause)
            call.respondApiError(ApiErrorCode.INFRASTRUCTURE_SERVICE_UNAVAILABLE)
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Internal server error: ${cause.message}", cause)
            call.respondApiError(ApiErrorCode.INFRASTRUCTURE_INTERNAL_ERROR)
        }
    }

    val jwtIssuer = environment.config.property("identity.jwt.issuer").getString()
    val jwtAudience = environment.config.property("identity.jwt.audience").getString()
    val jwtPublicKey = RsaKeyLoader.loadPublicKey(
        environment.config.property("identity.jwt.public_key_path").getString()
    )

    val sessionRepository by inject<SessionRepository>()
    val userRepository by inject<UserRepository>()
    val securityConfig by inject<SecurityConfig>()
    install(Authentication) {
        jwt {
            authHeader { call ->
                call.request.parseAuthorizationHeader()
                    ?: call.request.cookies["access_token"]?.let { HttpAuthHeader.Single("Bearer", it) }
            }
            verifier(
                JWT.require(Algorithm.RSA256(jwtPublicKey, null))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                val sid = credential.payload.getClaim("sid").asString()
                val session = sid?.let(sessionRepository::findById)
                if (session != null && session.revokedAt == null && session.expiresAt.isAfter(java.time.Instant.now())) JWTPrincipal(credential.payload) else null
            }
        }
    }

    install(SwaggerUI) {
        info {
            title = "Identity Service API"
            version = "1.0.0"
            description = "User identity management API"
        }
        security {
            securityScheme("BearerToken") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
                description = "Paste an API access token returned by POST /api/auth/token"
            }
        }
        tags {
            tag("Auth") { description = "Authentication and authorization" }
            tag("Users") { description = "User profile management" }
            tag("Sessions") { description = "Session management" }
            tag("Search") { description = "User search and lookup" }
            tag("System") { description = "System health" }
        }
        swagger {
            showTagFilterInput = true
        }
    }
    
    // 2. Inject Services
    val searchService by inject<SearchService>()
    val emailEventConsumer by inject<EmailEventConsumer>()
    val authController by inject<AuthController>()
    val userController by inject<UserController>()
    val searchController by inject<SearchController>()
    val sessionController by inject<SessionController>()
    val grpcPort = environment.config
        .propertyOrNull("identity.grpc.port")
        ?.getString()
        ?.toIntOrNull() ?: 9097
    
    // 3. Background Tasks
    val backgroundTasksEnabled = environment.config
        .propertyOrNull("identity.background.enabled")
        ?.getString()
        ?.toBoolean() ?: true

    if (backgroundTasksEnabled) {
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
    }
    
    // 3b. Start optional mTLS gRPC Server
    val grpcEnabled = environment.config.propertyOrNull("identity.grpc.enabled")?.getString()?.toBoolean() ?: false
    if (grpcEnabled) launch {
        try {
            val grpcServer = ProfileGrpcServer(
                userRepository, grpcPort,
                environment.config.property("identity.grpc.certificate").getString(),
                environment.config.property("identity.grpc.private_key").getString(),
                environment.config.property("identity.grpc.client_ca").getString(),
                environment.config.property("identity.grpc.allowed_client_sans").getString(),
                environment.config.propertyOrNull("identity.grpc.reflection")?.getString()?.toBoolean() ?: false
            )
            grpcServer.start()
        } catch (e: Exception) {
            log.error("Failed to start gRPC server: ${e.message}")
        }
    }

    // 4. Configure Routing
    routing {
        get("/internal/session-check") {
            if (call.request.headers["X-Internal-Auth"] != securityConfig.internalAuthSecret) {
                call.respond(HttpStatusCode.Forbidden); return@get
            }
            val session = call.request.queryParameters["sid"]?.let { runCatching { sessionRepository.findById(it) }.getOrNull() }
            val userId = call.request.queryParameters["uid"]
            if (session != null && session.userId == userId && session.revokedAt == null && session.expiresAt.isAfter(java.time.Instant.now())) {
                val user = userRepository.findById(userId)
                if (user == null || user.status != "ACTIVE") {
                    redisManager.revokeSession(session.id)
                    call.respond(HttpStatusCode.Unauthorized); return@get
                }
                call.respond(HttpStatusCode.NoContent)
            } else call.respond(HttpStatusCode.Unauthorized)
        }
        get("health", {
            tags = setOf("System")
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

private fun invalid(error: ApiErrorCode, vararg fields: String): ValidationResult.Invalid {
    return ValidationResult.Invalid("${error.name}|${fields.joinToString(",")}")
}

private suspend fun ApplicationCall.respondApiError(error: ApiErrorCode, fields: List<String> = emptyList()) {
    respond(
        error.status,
        ApiErrorResponse(
            code = error.name,
            numericCode = error.numericCode,
            message = error.message,
            fieldErrors = fields.map { ApiFieldError(it, error.name, error.numericCode) }
        )
    )
}
