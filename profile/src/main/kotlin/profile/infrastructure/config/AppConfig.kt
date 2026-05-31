package profile.infrastructure.config

data class AppConfig(
    val jwt: JwtConfig,
    val session: SessionConfig,
    val registration: RegistrationConfig,
    val postgres: PostgresConfig,
    val redis: RedisConfig,
    val smtp: SmtpConfig,
    val s3: S3Config
)

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val secret: String,
    val accessTokenExpMinutes: Long
)

data class SessionConfig(
    val refreshTokenExpDays: Long,
    val cookieSecure: Boolean
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
    val from: String
)

data class S3Config(
    val endpoint: String,
    val publicUrl: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String
)
