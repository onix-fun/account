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
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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
import profile.users.UpdateProfileRequest
import profile.infrastructure.db.SessionRepository
import profile.infrastructure.config.SecurityConfig
import profile.infrastructure.config.AppConfig
import profile.infrastructure.config.GrpcConfig
import profile.infrastructure.ratelimit.RateLimit
import profile.infrastructure.ratelimit.RateLimitConfig
import profile.infrastructure.redis.RedisManager
import profile.infrastructure.security.TrustedProxy
import profile.infrastructure.storage.S3Client
import profile.infrastructure.events.SmtpEmailSender
import profile.infrastructure.jwt.JwksProvider
import profile.users.userRouting
import javax.sql.DataSource
import java.lang.management.ManagementFactory

private const val MAX_BODY_SIZE = 5L * 1024 * 1024
 // 5MB, matches AVATAR_TOO_LARGE

@Serializable
private data class ReadinessResponse(val status: String, val checks: Map<String, Boolean>)

fun Application.module() {
    val securityConfig by inject<SecurityConfig>()

    // 1. Configure Plugins
    install(Koin) {
        slf4jLogger()
        modules(koinModule(environment.config))
    }

    install(XForwardedHeaders)

    install(CallLogging) {
        level = org.slf4j.event.Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }

    install(CORS) {
        val origins = securityConfig.allowedOrigins
        if (origins.contains("*") || origins.isEmpty()) {
            allowHost("localhost:5174", schemes = listOf("http", "https"))
            allowHost("127.0.0.1:5174", schemes = listOf("http", "https"))
            allowHost("localhost:8089", schemes = listOf("http", "https"))
            allowHost("localhost:8091", schemes = listOf("http", "https"))
        } else {
            origins.forEach { 
                val host = it.removePrefix("http://").removePrefix("https://")
                if (host.isNotBlank()) allowHost(host, schemes = listOf("http", "https"))
            }
        }
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Correlation-Id")
        allowHeader("X-CSRF-Token")
        allowCredentials = true
        maxAgeInSeconds = 86400
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        val csp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src * data: blob:; font-src 'self'; connect-src 'self' http://localhost:8089 http://127.0.0.1:8089 http://localhost:8091 http://127.0.0.1:8091; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"
        header("Content-Security-Policy", csp)
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
                request.username.trim().length < 3 -> invalid(ApiErrorCode.VALIDATION_USERNAME_TOO_SHORT, "username")
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
        validate<UpdateProfileRequest> { request ->
            when {
                request.username != null && request.username.isBlank() ->
                    invalid(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "username")
                request.username != null && request.username.trim().length < 3 ->
                    invalid(ApiErrorCode.VALIDATION_USERNAME_TOO_SHORT, "username")
                else -> ValidationResult.Valid
            }
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

    // CSRF validation
    intercept(ApplicationCallPipeline.Call) {
        val method = call.request.httpMethod
        if (method in setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)) {
            val path = call.request.path()
            val isCsrfExemptPath = path == "/api/auth/token" ||
                path == "/api/auth/token/refresh" ||
                path == "/api/auth/refresh" ||
                path == "/api/auth/csrf"
            val hasBearer = call.request.headers[HttpHeaders.Authorization]?.startsWith("Bearer ", ignoreCase = true) == true
            if (!isCsrfExemptPath && !hasBearer) {
                val headerToken = call.request.headers["X-CSRF-Token"] ?: ""
                val cookieToken = call.request.cookies["__Host-csrf_token"]
                    ?: call.request.cookies["csrf_token"]
                    ?: ""
                if (headerToken.isBlank() || cookieToken.isBlank() || !java.security.MessageDigest.isEqual(headerToken.toByteArray(), cookieToken.toByteArray())) {
                    call.respondApiError(ApiErrorCode.SECURITY_CSRF_INVALID)
                    return@intercept
                }
            }
        }
    }

    val redisManager by inject<RedisManager>()
    val applicationDataSource by inject<DataSource>()
    environment.monitor.subscribe(ApplicationStopping) {
        redisManager.close()
        (applicationDataSource as? AutoCloseable)?.close()
    }

    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            val parts = cause.reasons.firstOrNull()?.split("|", limit = 2).orEmpty()
            val error = parts.firstOrNull()?.let { runCatching { ApiErrorCode.valueOf(it) }.getOrNull() }
                ?: ApiErrorCode.VALIDATION_INVALID_REQUEST
            val fields = parts.getOrNull(1)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            call.respond(error.status, ApiErrorResponse(error.name, error.numericCode, error.message, fields.map { ApiFieldError(it, error.name, error.numericCode) }))
        }
        exception<ApiException> { call, cause ->
            call.respond(cause.error.status, ApiErrorResponse(cause.error.name, cause.error.numericCode, cause.error.message, cause.fields.map { ApiFieldError(it, cause.error.name, cause.error.numericCode) }))
        }
        exception<IllegalStateException> { call, cause ->
            call.application.log.error("Illegal state: ${cause.message}", cause)
            val error = ApiErrorCode.INFRASTRUCTURE_SERVICE_UNAVAILABLE
            call.respond(error.status, ApiErrorResponse(error.name, error.numericCode, error.message))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Internal server error: ${cause.message}", cause)
            val error = ApiErrorCode.INFRASTRUCTURE_INTERNAL_ERROR
            call.respond(error.status, ApiErrorResponse(error.name, error.numericCode, error.message))
        }
    }

    val appConfig by inject<AppConfig>()
    val grpcConfig by inject<GrpcConfig>()
    val jwtIssuer = appConfig.jwt.issuer
    val jwtAudience = appConfig.jwt.audience
    val jwtPublicKey = RsaKeyLoader.loadPublicKey(
        appConfig.jwt.publicKey
    )

    val sessionRepository by inject<SessionRepository>()
    val userRepository by inject<UserRepository>()
    install(Authentication) {
        jwt {
            authHeader { call ->
                val authHeader = call.request.parseAuthorizationHeader()
                if (authHeader != null) return@authHeader authHeader
                
                val cookieToken = call.request.cookies["__Host-access_token"] ?: call.request.cookies["access_token"]
                cookieToken?.let { HttpAuthHeader.Single("Bearer", it) }
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
                val isValid = session != null && session.revokedAt == null && session.expiresAt.isAfter(java.time.Instant.now())
                if (!isValid) {
                    application.log.warn("JWT validation failed: sid={}, session_found={}, revoked={}, expired={}", 
                        sid, session != null, session?.revokedAt != null, session?.expiresAt?.isBefore(java.time.Instant.now()) ?: "n/a")
                }
                if (isValid) JWTPrincipal(credential.payload) else null
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
    val dataSource by inject<DataSource>()
    val readinessRedis by inject<RedisManager>()
    val readinessS3 by inject<S3Client>()
    val readinessSmtp by inject<SmtpEmailSender>()
    val jwksProvider by inject<JwksProvider>()
    val role = appConfig.runtime.role
    val apiEnabled = role == "api" || role == "all"
    val workerEnabled = role == "worker" || role == "all"
    val backgroundEnabled = environment.config.propertyOrNull("identity.background.enabled")?.getString()?.toBoolean() ?: true
    
    // 3. Background Tasks
    if (apiEnabled && backgroundEnabled) {
        launch {
            try {
                searchService.indexAllUsers()
            } catch (e: Exception) {
                log.error("Failed to index users: ${e.message}")
            }
        }
    }
    if (workerEnabled) {
        emailEventConsumer.start()
        environment.monitor.subscribe(ApplicationStopping) { emailEventConsumer.stop() }
    }
    
    // 3b. Start optional mTLS gRPC Server
    if (apiEnabled && grpcConfig.enabled) launch {
        try {
            val grpcServer = ProfileGrpcServer(
                userRepository, grpcConfig.port,
                grpcConfig.certificate.orEmpty(),
                grpcConfig.privateKey.orEmpty(),
                grpcConfig.clientCa.orEmpty(),
                grpcConfig.allowedClientSans.joinToString(","),
                grpcConfig.reflection
            )
            grpcServer.start()
            environment.monitor.subscribe(ApplicationStopping) { grpcServer.stop() }
        } catch (e: Exception) {
            log.error("Failed to start gRPC server: ${e.message}")
        }
    }

    // 4. Configure Routing
    routing {
        get("livez", {
            tags = setOf("System")
            summary = "Liveness check"
            response { code(HttpStatusCode.OK) { description = "Process is alive" } }
        }) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
        }

        get("health") { call.respond(HttpStatusCode.OK, mapOf("status" to "UP")) }

        get("readyz") {
            val checks = linkedMapOf(
                "postgres" to runCatching { dataSource.connection.use { it.isValid(2) } }.getOrDefault(false),
                "redis" to readinessRedis.isReady(),
                "s3" to readinessS3.isReady(),
                "smtp" to readinessSmtp.isReady()
            )
            val ready = checks.values.all { it }
            call.respond(
                if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                ReadinessResponse(if (ready) "UP" else "DOWN", checks)
            )
        }

        get("metrics") {
            val uptime = ManagementFactory.getRuntimeMXBean().uptime / 1000.0
            call.respondText(
                "# HELP account_process_uptime_seconds Process uptime.\n# TYPE account_process_uptime_seconds gauge\naccount_process_uptime_seconds $uptime\n",
                ContentType.Text.Plain
            )
        }

        get("/.well-known/jwks.json") {
            call.respond(jwksProvider.document)
        }

        if (apiEnabled) {
            route("openapi.json") { openApiSpec("api") }
            route("swagger-ui") { swaggerUI("/openapi.json") }
            authRouting(authController, sessionController)
            userRouting(userController)
            searchRouting(searchController)
            sessionRouting(sessionController)
        }
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
