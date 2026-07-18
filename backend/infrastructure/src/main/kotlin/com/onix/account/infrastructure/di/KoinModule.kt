package com.onix.account.infrastructure.di

import io.ktor.server.config.*
import org.koin.dsl.module
import com.onix.account.auth.AuthController
import com.onix.account.auth.AuthService
import com.onix.account.auth.QrLoginService
import com.onix.account.infrastructure.config.*
import com.onix.account.infrastructure.db.DatabaseFactory
import com.onix.account.infrastructure.db.UserRepository
import com.onix.account.infrastructure.db.VerificationTokenRepository
import com.onix.account.infrastructure.db.SessionRepository
import com.onix.account.infrastructure.db.PendingEmailChangeRepository
import com.onix.account.infrastructure.db.QrLoginChallengeRepository
import com.onix.account.infrastructure.db.AccountLoginFailureRepository
import com.onix.account.infrastructure.db.AuditRepository
import com.onix.account.infrastructure.db.PendingRegistrationStore
import com.onix.account.infrastructure.db.RateLimitRepository
import com.onix.account.infrastructure.db.OrganizationRepository
import com.onix.account.infrastructure.events.EmailEventConsumer
import com.onix.account.infrastructure.events.EventPublisher
import com.onix.account.infrastructure.events.SmtpEmailSender
import com.onix.account.infrastructure.jwt.JwtIssuer
import com.onix.account.infrastructure.jwt.JwksProvider
import com.onix.account.infrastructure.redis.RedisManager
import com.onix.account.search.SearchController
import com.onix.account.search.SearchService
import com.onix.account.sessions.SessionController
import com.onix.account.sessions.SessionService
import com.onix.account.organizations.OrganizationController
import com.onix.account.organizations.OrganizationService
import com.onix.account.users.UserController
import com.onix.account.users.UserService

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
        downstream = DownstreamConfig(
            profileGrpcTarget = config.property("downstream.profile.target").getString(),
            profileApiKey = config.property("downstream.profile.api_key").getString(),
            mediaGrpcTarget = config.property("downstream.media.target").getString(),
            mediaApiKey = config.property("downstream.media.api_key").getString()
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
        com.onix.account.infrastructure.security.EmailNormalizer.normalize(appConfig.smtp.from, "smtp.from")
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
    single { appConfig.downstream }
    single { appConfig.security }
    single { appConfig.runtime }
    single { appConfig.grpc }
    single { config }

    // 2. Infrastructure
    single { DatabaseFactory.init(get()) }
    single { RedisManager(appConfig) }
    single { PendingRegistrationStore(get(), get()) }
    single { com.onix.account.infrastructure.clients.ProfileClient(appConfig.downstream) }
    single { com.onix.account.infrastructure.clients.MediaAvatarClient(appConfig.downstream) }
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
    single { OrganizationRepository(get()) }
    single { SearchService(get<UserRepository>(), get<RedisManager>()) }

    // 3. Services
    single { OrganizationService(get(), get(), get(), get(), get(), get()) }
    single { AuthService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { QrLoginService(get(), get(), get(), get()) }
    single { UserService(get(), get(), get(), get(), get()) }
    single { SessionService(get(), get()) }

    // 4. Controllers
    single { AuthController(get(), get(), get(), get(), get()) }
    single { UserController(get(), get()) }
    single { SearchController(get()) }
    single { SessionController(get()) }
    single { OrganizationController(get()) }
}
