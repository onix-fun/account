package profile.infrastructure.di

import io.ktor.server.config.*
import org.koin.dsl.module
import profile.auth.AuthController
import profile.auth.AuthService
import profile.infrastructure.config.*
import profile.infrastructure.db.DatabaseFactory
import profile.infrastructure.db.UserRepository
import profile.infrastructure.db.VerificationTokenRepository
import profile.infrastructure.db.SessionRepository
import profile.infrastructure.db.PendingEmailChangeRepository
import profile.infrastructure.db.AuditRepository
import profile.infrastructure.events.EmailEventConsumer
import profile.infrastructure.events.EventPublisher
import profile.infrastructure.events.SmtpEmailSender
import profile.infrastructure.jwt.JwtIssuer
import profile.infrastructure.redis.PendingRegistrationStore
import profile.infrastructure.redis.RedisManager
import profile.search.SearchController
import profile.search.SearchService
import profile.sessions.SessionController
import profile.sessions.SessionService
import profile.users.UserController
import profile.users.UserService

fun koinModule(config: ApplicationConfig) = module {
    fun env(name: String, fallback: () -> String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback()

    // 1. Typed Config
    val appConfig = AppConfig(
        jwt = JwtConfig(
            issuer = config.property("identity.jwt.issuer").getString(),
            audience = config.property("identity.jwt.audience").getString(),
            privateKeyPath = config.property("identity.jwt.private_key_path").getString(),
            publicKeyPath = config.property("identity.jwt.public_key_path").getString(),
            accessTokenExpMinutes = config.property("identity.jwt.access_token_exp_minutes").getString().toLong()
        ),
        session = SessionConfig(
            refreshTokenExpDays = config.property("identity.session.refresh_token_exp_days").getString().toLong(),
            cookieSecure = env("ACCOUNT_COOKIE_SECURE") {
                config.propertyOrNull("identity.session.cookie_secure")?.getString() ?: "false"
            }.toBoolean(),
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
            password = env("POSTGRES_PASSWORD") { config.property("postgres.password").getString() }
        ),
        redis = RedisConfig(
            url = config.property("redis.url").getString()
        ),
        smtp = SmtpConfig(
            host = env("SMTP_HOST") { config.property("smtp.host").getString() },
            port = env("SMTP_PORT") { config.property("smtp.port").getString() }.toInt(),
            from = env("SMTP_FROM") { config.propertyOrNull("smtp.from")?.getString() ?: "no-reply@account.local" },
            username = System.getenv("SMTP_USERNAME")?.takeIf { it.isNotBlank() }
                ?: config.propertyOrNull("smtp.username")?.getString()?.takeIf { it.isNotBlank() },
            password = System.getenv("SMTP_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: config.propertyOrNull("smtp.password")?.getString()?.takeIf { it.isNotBlank() },
            startTls = env("SMTP_START_TLS") {
                config.propertyOrNull("smtp.start_tls")?.getString() ?: "false"
            }.toBoolean()
        ),
        s3 = S3Config(
            endpoint = config.property("s3.endpoint").getString(),
            publicUrl = env("S3_PUBLIC_URL") { config.propertyOrNull("s3.public_url")?.getString() ?: config.property("s3.endpoint").getString() },
            accessKey = env("S3_ACCESS_KEY") { config.property("s3.access_key").getString() },
            secretKey = env("S3_SECRET_KEY") { config.property("s3.secret_key").getString() },
            bucket = config.property("s3.bucket").getString()
        ),
        security = SecurityConfig(
            otpHmacSecret = env("IDENTITY_OTP_HMAC_SECRET") { config.propertyOrNull("identity.security.otp_hmac_secret")?.getString() ?: "" },
            internalAuthSecret = env("INTERNAL_AUTH_SECRET") { config.propertyOrNull("identity.security.internal_auth_secret")?.getString() ?: "" },
            trustedProxyCidrs = config.propertyOrNull("identity.security.trusted_proxy_cidrs")?.getString()
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("127.0.0.1/32", "::1/128"),
            allowedOrigins = config.propertyOrNull("identity.security.allowed_origins")?.getString()
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("http://localhost:5174", "http://127.0.0.1:5174")
        ),
        environment = env("APP_ENV") { config.propertyOrNull("app.environment")?.getString() ?: "development" }
    )
    if (appConfig.environment == "production") {
        require(appConfig.session.cookieSecure) { "Secure cookies are required in production" }
        require(appConfig.security.otpHmacSecret.length >= 32) { "IDENTITY_OTP_HMAC_SECRET must be at least 32 characters" }
        require(appConfig.smtp.startTls) { "SMTP STARTTLS is required in production" }
        profile.infrastructure.security.EmailNormalizer.normalize(appConfig.smtp.from, "smtp.from")
        require(appConfig.s3.publicUrl.startsWith("https://")) { "Public avatar URL must use HTTPS in production" }
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
    single { config }

    // 2. Infrastructure
    single { DatabaseFactory.init(get()) }
    single { RedisManager(config) }
    single { PendingRegistrationStore(get(), get()) }
    single { profile.infrastructure.storage.S3Client(config) }
    single { EventPublisher(get(), get()) }
    single { SmtpEmailSender(get()) }
    single { EmailEventConsumer(get(), get(), get()) }
    single { JwtIssuer(get()) }
    single { UserRepository(get()) }
    single { SessionRepository(get()) }
    single { VerificationTokenRepository(get()) }
    single { PendingEmailChangeRepository(get()) }
    single { AuditRepository(get()) }
    single { SearchService(get<UserRepository>(), get<RedisManager>()) }

    // 3. Services
    single { AuthService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { UserService(get(), get(), get(), get()) }
    single { SessionService(get(), get()) }

    // 4. Controllers
    single { AuthController(get(), get(), get(), get()) }
    single { UserController(get(), get()) }
    single { SearchController(get()) }
    single { SessionController(get()) }
}
