package profile.infrastructure.di

import io.ktor.server.config.*
import org.koin.dsl.module
import profile.auth.AuthController
import profile.auth.AuthService
import profile.auth.QrLoginService
import profile.infrastructure.config.*
import profile.infrastructure.db.DatabaseFactory
import profile.infrastructure.db.UserRepository
import profile.infrastructure.db.VerificationTokenRepository
import profile.infrastructure.db.SessionRepository
import profile.infrastructure.db.PendingEmailChangeRepository
import profile.infrastructure.db.QrLoginChallengeRepository
import profile.infrastructure.db.AccountLoginFailureRepository
import profile.infrastructure.db.AuditRepository
import profile.infrastructure.db.PendingRegistrationStore
import profile.infrastructure.db.RateLimitRepository
import profile.infrastructure.events.EmailEventConsumer
import profile.infrastructure.events.EventPublisher
import profile.infrastructure.events.SmtpEmailSender
import profile.infrastructure.jwt.JwtIssuer
import profile.infrastructure.jwt.JwksProvider
import profile.infrastructure.redis.RedisManager
import profile.search.SearchController
import profile.search.SearchService
import profile.sessions.SessionController
import profile.sessions.SessionService
import profile.users.UserController
import profile.users.UserService

fun appConfigFrom(config: ApplicationConfig): AppConfig {
    return AppConfig(
        jwt = JwtConfig(
            issuer = config.property("identity.jwt.issuer").getString(),
            audience = config.property("identity.jwt.audience").getString(),
            privateKey = config.propertyOrNull("identity.jwt.private_key")?.getString()
                ?: config.property("identity.jwt.private_key_path").getString(),
            publicKey = config.propertyOrNull("identity.jwt.public_key")?.getString()
                ?: config.property("identity.jwt.public_key_path").getString(),
            activeKid = config.propertyOrNull("identity.jwt.active_kid")?.getString() ?: "active",
            previousPublicKeys = config.propertyOrNull("identity.jwt.previous_public_keys")?.getString()
                ?.split(",")
                ?.mapNotNull {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2 && parts.all(String::isNotBlank)) parts[0].trim() to parts[1].trim() else null
                }?.toMap().orEmpty(),
            accessTokenExpMinutes = config.property("identity.jwt.access_token_exp_minutes").getString().toLong()
        ),
        session = SessionConfig(
            refreshTokenExpDays = config.property("identity.session.refresh_token_exp_days").getString().toLong(),
            cookieSecure = config.propertyOrNull("identity.session.cookie_secure")?.getString()?.toBoolean() ?: false,
            cookieDomain = config.propertyOrNull("identity.session.cookie_domain")
                ?.getString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        ),
        registration = RegistrationConfig(
            pendingTtlSeconds = config
                .propertyOrNull("identity.registration.pending_ttl_seconds")
                ?.getString()
                ?.toLong() ?: 3600,
            allowInMemoryFallback = config
                .propertyOrNull("identity.registration.allow_in_memory_fallback")
                ?.getString()
                ?.toBoolean() ?: false
        ),
        postgres = PostgresConfig(
            url = config.property("postgres.url").getString(),
            user = config.property("postgres.user").getString(),
            password = config.property("postgres.password").getString()
        ),
        redis = RedisConfig(
            url = config.property("redis.url").getString()
        ),
        smtp = SmtpConfig(
            host = config.property("smtp.host").getString(),
            port = config.property("smtp.port").getString().toInt(),
            from = config.propertyOrNull("smtp.from")?.getString() ?: "no-reply@account.local",
            username = config.propertyOrNull("smtp.username")?.getString()?.takeIf { it.isNotBlank() },
            password = config.propertyOrNull("smtp.password")?.getString()?.takeIf { it.isNotBlank() },
            startTls = config.propertyOrNull("smtp.start_tls")?.getString()?.toBoolean() ?: false
        ),
        s3 = S3Config(
            endpoint = config.property("s3.endpoint").getString(),
            publicUrl = config.propertyOrNull("s3.public_url")?.getString() ?: config.property("s3.endpoint").getString(),
            accessKey = config.property("s3.access_key").getString(),
            secretKey = config.property("s3.secret_key").getString(),
            bucket = config.property("s3.bucket").getString()
        ),
        security = SecurityConfig(
            otpHmacSecret = config.propertyOrNull("identity.security.otp_hmac_secret")?.getString() ?: "",
            internalAuthSecret = config.propertyOrNull("identity.security.internal_auth_secret")?.getString() ?: "",
            trustedProxyCidrs = config.propertyOrNull("identity.security.trusted_proxy_cidrs")?.getString()
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("127.0.0.1/32", "::1/128"),
            allowedOrigins = config.propertyOrNull("identity.security.allowed_origins")?.getString()
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("http://localhost:5174", "http://127.0.0.1:5174")
        ),
        environment = config.propertyOrNull("app.environment")?.getString() ?: "development",
        runtime = RuntimeConfig(config.propertyOrNull("account.runtime.role")?.getString() ?: "all"),
        grpc = GrpcConfig(
            enabled = config.propertyOrNull("identity.grpc.enabled")?.getString()?.toBoolean() ?: false,
            port = config.propertyOrNull("identity.grpc.port")?.getString()?.toIntOrNull() ?: 9097,
            certificate = config.propertyOrNull("identity.grpc.certificate")?.getString()?.takeIf(String::isNotBlank),
            privateKey = config.propertyOrNull("identity.grpc.private_key")?.getString()?.takeIf(String::isNotBlank),
            clientCa = config.propertyOrNull("identity.grpc.client_ca")?.getString()?.takeIf(String::isNotBlank),
            allowedClientSans = config.propertyOrNull("identity.grpc.allowed_client_sans")?.getString()
                ?.split(",")?.map(String::trim)?.filter(String::isNotBlank).orEmpty(),
            reflection = config.propertyOrNull("identity.grpc.reflection")?.getString()?.toBoolean() ?: false
        )
    )
}

fun koinModule(config: ApplicationConfig) = module {
    // 1. Typed Config
    val appConfig = appConfigFrom(config)
    if (appConfig.environment == "production") {
        require(appConfig.session.cookieSecure) { "Secure cookies are required in production" }
        require(appConfig.security.otpHmacSecret.length >= 32) { "ACCOUNT_SECURITY_OTP_HMAC_SECRET must be at least 32 characters" }
        require(appConfig.security.internalAuthSecret.length >= 32) { "ACCOUNT_SECURITY_INTERNAL_AUTH_SECRET must be at least 32 characters" }
        require(appConfig.smtp.startTls) { "SMTP STARTTLS is required in production" }
        profile.infrastructure.security.EmailNormalizer.normalize(appConfig.smtp.from, "smtp.from")
        require(appConfig.s3.publicUrl.startsWith("https://")) { "Public avatar URL must use HTTPS in production" }
        if (appConfig.grpc.enabled) {
            require(!appConfig.grpc.certificate.isNullOrBlank()) { "gRPC server certificate is required in production" }
            require(!appConfig.grpc.privateKey.isNullOrBlank()) { "gRPC private key is required in production" }
            require(!appConfig.grpc.clientCa.isNullOrBlank()) { "gRPC client CA is required in production" }
            require(appConfig.grpc.allowedClientSans.isNotEmpty()) { "gRPC client SAN allowlist is required in production" }
        }
    }
    
    single { appConfig }
    single { appConfig.jwt }
    single { appConfig.session }
    single { appConfig.registration }
    single { appConfig.postgres }
    single { appConfig.redis }
    single { appConfig.smtp }
    single { appConfig.s3 }
    single { appConfig.security }
    single { appConfig.runtime }
    single { appConfig.grpc }
    single { config }

    // 2. Infrastructure
    single { DatabaseFactory.init(get()) }
    single { RedisManager(appConfig) }
    single { PendingRegistrationStore(get(), get()) }
    single { profile.infrastructure.storage.S3Client(appConfig.s3) }
    single { EventPublisher(get(), get()) }
    single { SmtpEmailSender(get()) }
    single { EmailEventConsumer(get(), get(), get()) }
    single { JwtIssuer(appConfig.jwt) }
    single { JwksProvider(appConfig.jwt) }
    single { UserRepository(get()) }
    single { SessionRepository(get()) }
    single { QrLoginChallengeRepository(get()) }
    single { VerificationTokenRepository(get()) }
    single { PendingEmailChangeRepository(get()) }
    single { AccountLoginFailureRepository(get()) }
    single { RateLimitRepository(get()) }
    single { AuditRepository(get()) }
    single { SearchService(get<UserRepository>(), get<RedisManager>()) }

    // 3. Services
    single { AuthService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { QrLoginService(get(), get(), get(), get()) }
    single { UserService(get(), get(), get(), get()) }
    single { SessionService(get(), get()) }

    // 4. Controllers
    single { AuthController(get(), get(), get(), get(), get()) }
    single { UserController(get(), get()) }
    single { SearchController(get()) }
    single { SessionController(get()) }
}
