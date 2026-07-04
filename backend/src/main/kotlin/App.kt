package bootstrap

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
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import profile.auth.*
import profile.grpc.ProfileServiceImpl
import profile.infrastructure.config.*
import profile.infrastructure.db.UserRepository
import profile.infrastructure.db.SessionRepository
import profile.infrastructure.di.koinModule
import profile.infrastructure.events.EmailEventConsumer
import profile.infrastructure.jwt.RsaKeyLoader
import profile.infrastructure.ratelimit.RateLimit
import profile.infrastructure.redis.RedisManager
import profile.infrastructure.storage.S3Client
import profile.infrastructure.events.SmtpEmailSender
import profile.infrastructure.jwt.JwksProvider
import profile.search.*
import profile.sessions.*
import profile.socialModule
import profile.api.grpc.SocialGrpcService
import profile.api.rest.*
import profile.users.*
import java.lang.management.ManagementFactory
import javax.sql.DataSource

private const val MAX_BODY_SIZE = 5L * 1024 * 1024

@kotlinx.serialization.Serializable
private data class ReadinessResponse(val status: String, val checks: Map<String, Boolean>)

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(koinModule(environment.config), socialModule)
    }

    install(XForwardedHeaders)

    val securityConfig by inject<SecurityConfig>()

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
        header("Permissions-Policy", "camera=(self), microphone=(), geolocation=()")
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
        val contentLength = context.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_BODY_SIZE) {
            context.respond(HttpStatusCode.PayloadTooLarge, profile.shared.ApiErrorResponse(
                code = profile.shared.ApiErrorCode.AVATAR_TOO_LARGE.name,
                numericCode = profile.shared.ApiErrorCode.AVATAR_TOO_LARGE.numericCode,
                message = profile.shared.ApiErrorCode.AVATAR_TOO_LARGE.message,
                fieldErrors = emptyList()
            ))
            return@intercept
        }
    }

    install(RequestValidation) {
        validate<RegisterRequest> { request ->
            when {
                request.email.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "email")
                !request.email.contains("@") -> invalid(profile.shared.ApiErrorCode.VALIDATION_INVALID_EMAIL, "email")
                request.username.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "username")
                request.username.trim().length < 3 -> invalid(profile.shared.ApiErrorCode.VALIDATION_USERNAME_TOO_SHORT, "username")
                request.password.length < 8 -> invalid(profile.shared.ApiErrorCode.VALIDATION_PASSWORD_TOO_SHORT, "password")
                else -> ValidationResult.Valid
            }
        }
        validate<ConfirmRegistrationRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            when {
                identifier.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                !request.code.matches(Regex("\\d{6}")) -> invalid(profile.shared.ApiErrorCode.VALIDATION_INVALID_CODE, "code")
                else -> ValidationResult.Valid
            }
        }
        validate<ResendRegistrationCodeRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            when {
                identifier.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                else -> ValidationResult.Valid
            }
        }
        validate<LoginRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            when {
                identifier.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                request.password.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "password")
                else -> ValidationResult.Valid
            }
        }
        validate<ForgotPasswordRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            when {
                identifier.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                else -> ValidationResult.Valid
            }
        }
        validate<ResetPasswordRequest> { request ->
            val identifier = request.identifier ?: request.email.orEmpty()
            val code = request.code ?: request.token.orEmpty()
            when {
                identifier.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                !code.matches(Regex("\\d{6}")) -> invalid(profile.shared.ApiErrorCode.VALIDATION_INVALID_CODE, "code")
                request.newPassword.length < 8 -> invalid(profile.shared.ApiErrorCode.VALIDATION_PASSWORD_TOO_SHORT, "newPassword")
                else -> ValidationResult.Valid
            }
        }
        validate<ChangePasswordRequest> { request ->
            when {
                request.currentPassword.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "currentPassword")
                request.newPassword.length < 8 -> invalid(profile.shared.ApiErrorCode.VALIDATION_PASSWORD_TOO_SHORT, "newPassword")
                else -> ValidationResult.Valid
            }
        }
        validate<DeleteAccountRequest> { request ->
            when {
                request.password.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "password")
                else -> ValidationResult.Valid
            }
        }
        validate<PublicVerificationRequest> { request ->
            if (request.identifier.isBlank()) invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
            else ValidationResult.Valid
        }
        validate<PublicVerificationConfirmRequest> { request ->
            when {
                request.identifier.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
                !request.code.matches(Regex("\\d{6}")) -> invalid(profile.shared.ApiErrorCode.VALIDATION_INVALID_CODE, "code")
                else -> ValidationResult.Valid
            }
        }
        validate<QrLoginConsumeRequest> { request ->
            val hasScanToken = !request.scanToken.isNullOrBlank()
            val hasManualCode = !request.manualCode.isNullOrBlank()
            when {
                hasScanToken == hasManualCode -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "scanToken,manualCode")
                hasManualCode && request.manualCode.orEmpty().filter(Char::isLetterOrDigit).length != 12 ->
                    invalid(profile.shared.ApiErrorCode.AUTH_QR_CODE_INVALID, "manualCode")
                else -> ValidationResult.Valid
            }
        }
        validate<RequestEmailChangeRequest> { request ->
            when {
                request.currentPassword.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "currentPassword")
                request.newEmail.isBlank() -> invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "newEmail")
                else -> ValidationResult.Valid
            }
        }
        validate<ConfirmEmailChangeRequest> { request ->
            if (!request.code.matches(Regex("\\d{6}"))) invalid(profile.shared.ApiErrorCode.VALIDATION_INVALID_CODE, "code")
            else ValidationResult.Valid
        }
        validate<UpdateProfileRequest> { request ->
            val u = request.username
            when {
                u != null && u.isBlank() ->
                    invalid(profile.shared.ApiErrorCode.VALIDATION_REQUIRED_FIELD, "username")
                u != null && u.trim().length < 3 ->
                    invalid(profile.shared.ApiErrorCode.VALIDATION_USERNAME_TOO_SHORT, "username")
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
        route("/api/auth/qr/consume", max = 20, windowSeconds = 60)
        route("/api/auth/qr/challenges", max = 20, windowSeconds = 60)
        route("/api/auth/logout", max = 30, windowSeconds = 60)
        route("/api/users/me/avatar", max = 20, windowSeconds = 60)
    }

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
                    call.respondApiError(profile.shared.ApiErrorCode.SECURITY_CSRF_INVALID)
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
            val error = parts.firstOrNull()?.let { runCatching { profile.shared.ApiErrorCode.valueOf(it) }.getOrNull() }
                ?: profile.shared.ApiErrorCode.VALIDATION_INVALID_REQUEST
            val fields = parts.getOrNull(1)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            call.respond(error.status, profile.shared.ApiErrorResponse(error.name, error.numericCode, error.message, fields.map { profile.shared.ApiFieldError(it, error.name, error.numericCode) }))
        }
        exception<profile.shared.ApiException> { call, cause ->
            call.respond(cause.error.status, profile.shared.ApiErrorResponse(cause.error.name, cause.error.numericCode, cause.error.message, cause.fields.map { profile.shared.ApiFieldError(it, cause.error.name, cause.error.numericCode) }))
        }
        exception<UnauthorizedException> { call, _ ->
            val error = profile.shared.ApiErrorCode.AUTH_UNAUTHORIZED
            call.respond(error.status, profile.shared.ApiErrorResponse(error.name, error.numericCode, error.message))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.application.log.warn("Invalid request: ${cause.message}")
            val error = profile.shared.ApiErrorCode.VALIDATION_INVALID_REQUEST
            call.respond(error.status, profile.shared.ApiErrorResponse(error.name, error.numericCode, cause.message ?: error.message))
        }
        exception<IllegalStateException> { call, cause ->
            call.application.log.error("Illegal state: ${cause.message}", cause)
            val error = profile.shared.ApiErrorCode.INFRASTRUCTURE_SERVICE_UNAVAILABLE
            call.respond(error.status, profile.shared.ApiErrorResponse(error.name, error.numericCode, error.message))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Internal server error: ${cause.message}", cause)
            val error = profile.shared.ApiErrorCode.INFRASTRUCTURE_INTERNAL_ERROR
            call.respond(error.status, profile.shared.ApiErrorResponse(error.name, error.numericCode, error.message))
        }
    }

    val appConfig by inject<AppConfig>()
    val grpcConfig by inject<GrpcConfig>()
    val jwtIssuer = appConfig.jwt.issuer
    val jwtAudience = appConfig.jwt.audience
    val jwtPublicKey = RsaKeyLoader.loadPublicKey(appConfig.jwt.publicKey)

    val sessionRepository by inject<SessionRepository>()
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
                val session = sid?.let { sessionRepository.findById(it) }
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
            title = "Account Service API"
            version = "1.0.0"
            description = "Combined identity and social API"
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
            tag("Social") { description = "Social features (follow, block, friends)" }
            tag("Notifications") { description = "In-app notifications" }
            tag("System") { description = "System health" }
        }
        swagger {
            showTagFilterInput = true
        }
    }

    val searchService by inject<profile.search.SearchService>()
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
    val appRole = appConfig.runtime.role
    val apiEnabled = appRole == "api" || appRole == "all"
    val workerEnabled = appRole == "worker" || appRole == "all"
    val backgroundEnabled = environment.config.propertyOrNull("identity.background.enabled")?.getString()?.toBoolean() ?: true

    val userService by inject<UserService>()
    val socialGrpcService by inject<SocialGrpcService>()
    val socialUseCases by inject<profile.usecases.SocialUseCases>()
    val notificationUseCases by inject<profile.usecases.NotificationUseCases>()
    val sseManager by inject<profile.infrastructure.SseManager>()
    val eventBus by inject<profile.infrastructure.EventBus>()
    val notificationOutboxWorker by inject<profile.infrastructure.NotificationOutboxWorker>()
    val birthdayNotificationService by inject<profile.usecases.BirthdayNotificationService>()
    val socialRepo by inject<profile.infrastructure.SocialRepo>()
    val privacyRepo by inject<profile.infrastructure.PrivacyRepo>()
    val notificationRepo by inject<profile.infrastructure.NotificationRepo>()

    val userRepo by inject<profile.infrastructure.db.UserRepository>()

    eventBus.start()
    environment.monitor.subscribe(ApplicationStopping) { eventBus.stop() }

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
        notificationOutboxWorker.start()
        environment.monitor.subscribe(ApplicationStopping) { emailEventConsumer.stop() }
        environment.monitor.subscribe(ApplicationStopping) { notificationOutboxWorker.stop() }
    }

    if (apiEnabled && grpcConfig.enabled) launch {
        try {
            val identityService = profile.grpc.ProfileServiceImpl(userRepo)
            val server = GrpcServer(
                identityService = identityService,
                socialGrpcService = socialGrpcService,
                port = grpcConfig.port,
                certificate = grpcConfig.certificate.orEmpty(),
                privateKey = grpcConfig.privateKey.orEmpty(),
                clientCa = grpcConfig.clientCa.orEmpty(),
                allowedSans = grpcConfig.allowedClientSans.joinToString(","),
                reflection = grpcConfig.reflection
            )
            server.start()
            environment.monitor.subscribe(ApplicationStopping) { server.stop() }
        } catch (e: Exception) {
            log.error("Failed to start gRPC server: ${e.message}")
        }
    }

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

            authenticate {
                searchRoutes(searchService, socialUseCases)
                profileRoutes(userService, socialRepo, privacyRepo, socialUseCases, notificationRepo, birthdayNotificationService)
                socialRoutes(userService, socialUseCases, notificationUseCases, sseManager)
                notificationRoutes(userService, notificationUseCases, sseManager)
                settingsRoutes(socialUseCases, notificationUseCases)
            }
        }
    }
}

private fun invalid(error: profile.shared.ApiErrorCode, vararg fields: String): ValidationResult.Invalid {
    return ValidationResult.Invalid("${error.name}|${fields.joinToString(",")}")
}

private suspend fun io.ktor.server.application.ApplicationCall.respondApiError(error: profile.shared.ApiErrorCode, fields: List<String> = emptyList()) {
    respond(
        error.status,
        profile.shared.ApiErrorResponse(
            code = error.name,
            numericCode = error.numericCode,
            message = error.message,
            fieldErrors = fields.map { profile.shared.ApiFieldError(it, error.name, error.numericCode) }
        )
    )
}
