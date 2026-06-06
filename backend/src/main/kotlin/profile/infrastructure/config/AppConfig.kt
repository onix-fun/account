package profile.infrastructure.config

data class AppConfig(
    val jwt: JwtConfig,
    val session: SessionConfig,
    val registration: RegistrationConfig,
    val postgres: PostgresConfig,
    val redis: RedisConfig,
    val smtp: SmtpConfig,
    val s3: S3Config,
    val security: SecurityConfig,
    val environment: String
)

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val privateKeyPath: String,
    val publicKeyPath: String,
    val accessTokenExpMinutes: Long
)

data class SessionConfig(
    val refreshTokenExpDays: Long,
    val cookieSecure: Boolean,
    val cookieDomain: String?
)

data class RegistrationConfig(
    val pendingTtlSeconds: Long,
    val allowInMemoryFallback: Boolean
)

data class PostgresConfig(
    val url: String,
    val user: String,
    val password: String
)

data class RedisConfig(
    val url: String
)

data class SmtpConfig(
    val host: String,
    val port: Int,
    val from: String,
    val username: String?,
    val password: String?,
    val startTls: Boolean
)

data class SecurityConfig(
    val otpHmacSecret: String,
    val internalAuthSecret: String
)

data class S3Config(
    val endpoint: String,
    val publicUrl: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String
)
