package profile.infrastructure.config

import io.ktor.server.config.MapApplicationConfig
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

object EnvConfig {
    private val roles = setOf("api", "worker", "all")

    fun load(env: Map<String, String> = System.getenv(), roleOverride: String? = null): MapApplicationConfig {
        fun value(name: String, default: String? = null, secret: Boolean = false): String {
            val direct = env[name]?.takeIf(String::isNotBlank)
            val file = env["${name}_FILE"]?.takeIf(String::isNotBlank)
            require(!(direct != null && file != null)) { "$name and ${name}_FILE cannot be set together" }
            if (file != null) return Files.readString(Path.of(file)).trim()
            if (direct != null) return direct
            return default ?: error("$name is required")
        }

        fun optional(name: String, secret: Boolean = false): String? {
            val direct = env[name]?.takeIf(String::isNotBlank)
            val file = env["${name}_FILE"]?.takeIf(String::isNotBlank)
            require(!(direct != null && file != null)) { "$name and ${name}_FILE cannot be set together" }
            return file?.let { Files.readString(Path.of(it)).trim() } ?: direct
        }

        fun boolean(name: String, default: String): String {
            val raw = value(name, default)
            require(raw == "true" || raw == "false") { "$name must be true or false" }
            return raw
        }

        fun positiveLong(name: String, default: String): String {
            val raw = value(name, default)
            require((raw.toLongOrNull() ?: 0) > 0) { "$name must be a positive integer" }
            return raw
        }

        val appEnv = value("ACCOUNT_ENV", "development").lowercase()
        require(appEnv in setOf("development", "production", "test")) { "ACCOUNT_ENV must be development, test or production" }
        val role = (roleOverride ?: value("ACCOUNT_ROLE", "all")).lowercase()
        require(role in roles) { "role must be api, worker or all" }
        val grpcEnabled = boolean("ACCOUNT_GRPC_ENABLED", "true")
        val grpcCert = optional("ACCOUNT_GRPC_CERTIFICATE", secret = true)
        val grpcKey = optional("ACCOUNT_GRPC_PRIVATE_KEY", secret = true)
        val grpcClientCa = optional("ACCOUNT_GRPC_CLIENT_CA", secret = true)
        val grpcSans = value("ACCOUNT_GRPC_ALLOWED_CLIENT_SANS", "")
        val cookieSecure = boolean("ACCOUNT_HTTP_COOKIE_SECURE", "false")
        val smtpTls = boolean("ACCOUNT_SMTP_STARTTLS", "false")
        val s3PublicUrl = value("ACCOUNT_S3_PUBLIC_URL", "/api/avatars")

        URI(value("ACCOUNT_DATABASE_JDBC_URL"))
        URI(value("ACCOUNT_REDIS_URL"))
        URI(value("ACCOUNT_S3_ENDPOINT"))
        if (s3PublicUrl.startsWith("http")) URI(s3PublicUrl)

        if (appEnv == "production") {
            require(cookieSecure == "true") { "ACCOUNT_HTTP_COOKIE_SECURE must be true in production" }
            require(smtpTls == "true") { "ACCOUNT_SMTP_STARTTLS must be true in production" }
            require(s3PublicUrl.startsWith("https://")) { "ACCOUNT_S3_PUBLIC_URL must use HTTPS in production" }
            if (grpcEnabled == "true") {
                require(!grpcCert.isNullOrBlank()) { "ACCOUNT_GRPC_CERTIFICATE or ACCOUNT_GRPC_CERTIFICATE_FILE is required in production" }
                require(!grpcKey.isNullOrBlank()) { "ACCOUNT_GRPC_PRIVATE_KEY or ACCOUNT_GRPC_PRIVATE_KEY_FILE is required in production" }
                require(!grpcClientCa.isNullOrBlank()) { "ACCOUNT_GRPC_CLIENT_CA or ACCOUNT_GRPC_CLIENT_CA_FILE is required in production" }
                require(grpcSans.isNotBlank()) { "ACCOUNT_GRPC_ALLOWED_CLIENT_SANS is required in production" }
            }
        }

        val config = MapApplicationConfig()
        fun put(key: String, value: String?) {
            if (value != null) config.put(key, value)
        }

        put("ktor.deployment.port", positiveLong("ACCOUNT_HTTP_PORT", "8080"))
        put("ktor.deployment.host", value("ACCOUNT_HTTP_HOST", "0.0.0.0"))
        put("account.runtime.role", role)
        put("app.environment", appEnv)
        put("identity.jwt.issuer", value("ACCOUNT_JWT_ISSUER", "account-service"))
        put("identity.jwt.audience", value("ACCOUNT_JWT_AUDIENCE", "account"))
        put("identity.jwt.private_key", value("ACCOUNT_JWT_PRIVATE_KEY", secret = true))
        put("identity.jwt.public_key", value("ACCOUNT_JWT_PUBLIC_KEY", secret = true))
        put("identity.jwt.active_kid", value("ACCOUNT_JWT_ACTIVE_KID"))
        put("identity.jwt.previous_public_keys", value("ACCOUNT_JWT_PREVIOUS_PUBLIC_KEYS", ""))
        put("identity.jwt.access_token_exp_minutes", positiveLong("ACCOUNT_JWT_ACCESS_TOKEN_EXP_MINUTES", "15"))
        put("identity.session.refresh_token_exp_days", positiveLong("ACCOUNT_SESSION_REFRESH_TOKEN_EXP_DAYS", "30"))
        put("identity.session.cookie_secure", cookieSecure)
        put("identity.session.cookie_domain", value("ACCOUNT_HTTP_COOKIE_DOMAIN", ""))
        put("identity.registration.pending_ttl_seconds", positiveLong("ACCOUNT_REGISTRATION_PENDING_TTL_SECONDS", "3600"))
        put("identity.registration.allow_in_memory_fallback", boolean("ACCOUNT_REGISTRATION_ALLOW_IN_MEMORY_FALLBACK", "false"))
        put("postgres.url", value("ACCOUNT_DATABASE_JDBC_URL"))
        put("postgres.user", value("ACCOUNT_DATABASE_USERNAME"))
        put("postgres.password", value("ACCOUNT_DATABASE_PASSWORD", secret = true))
        put("redis.url", value("ACCOUNT_REDIS_URL", secret = true))
        put("smtp.host", value("ACCOUNT_SMTP_HOST"))
        put("smtp.port", positiveLong("ACCOUNT_SMTP_PORT", "587"))
        put("smtp.from", value("ACCOUNT_SMTP_FROM"))
        put("smtp.username", optional("ACCOUNT_SMTP_USERNAME", secret = true))
        put("smtp.password", optional("ACCOUNT_SMTP_PASSWORD", secret = true))
        put("smtp.start_tls", smtpTls)
        put("s3.endpoint", value("ACCOUNT_S3_ENDPOINT"))
        put("s3.public_url", s3PublicUrl)
        put("s3.access_key", value("ACCOUNT_S3_ACCESS_KEY", secret = true))
        put("s3.secret_key", value("ACCOUNT_S3_SECRET_KEY", secret = true))
        put("s3.bucket", value("ACCOUNT_S3_BUCKET", "avatars"))
        put("identity.security.otp_hmac_secret", value("ACCOUNT_SECURITY_OTP_HMAC_SECRET", secret = true))
        put("identity.security.internal_auth_secret", value("ACCOUNT_SECURITY_INTERNAL_AUTH_SECRET", secret = true))
        put("identity.security.trusted_proxy_cidrs", value("ACCOUNT_SECURITY_TRUSTED_PROXY_CIDRS", "127.0.0.1/32,::1/128"))
        put("identity.security.allowed_origins", value("ACCOUNT_HTTP_ALLOWED_ORIGINS", ""))
        put("identity.grpc.enabled", grpcEnabled)
        put("identity.grpc.port", positiveLong("ACCOUNT_GRPC_PORT", "9097"))
        put("identity.grpc.certificate", grpcCert)
        put("identity.grpc.private_key", grpcKey)
        put("identity.grpc.client_ca", grpcClientCa)
        put("identity.grpc.allowed_client_sans", grpcSans)
        put("identity.grpc.reflection", boolean("ACCOUNT_GRPC_REFLECTION", "false"))
        return config
    }
}
