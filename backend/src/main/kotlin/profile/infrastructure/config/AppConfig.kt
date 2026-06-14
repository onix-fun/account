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
    val environment: String,
    val runtime: RuntimeConfig,
    val grpc: GrpcConfig
)

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val privateKey: String,
    val publicKey: String,
    val activeKid: String,
    val previousPublicKeys: Map<String, String>,
    val accessTokenExpMinutes: Long
)

data class RuntimeConfig(val role: String)

data class GrpcConfig(
    val enabled: Boolean,
    val port: Int,
    val certificate: String?,
    val privateKey: String?,
    val clientCa: String?,
    val allowedClientSans: List<String>,
    val reflection: Boolean
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
    val internalAuthSecret: String,
    val trustedProxyCidrs: List<String>,
    val allowedOrigins: List<String>
)

data class S3Config(
    val endpoint: String,
    val publicUrl: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String
)
