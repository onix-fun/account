package profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import profile.infrastructure.config.EnvConfig

class EnvConfigTest {
    private fun validEnv() = mutableMapOf(
        "ACCOUNT_DATABASE_JDBC_URL" to "jdbc:postgresql://postgres:5432/account",
        "ACCOUNT_DATABASE_USERNAME" to "account",
        "ACCOUNT_DATABASE_PASSWORD" to "password",
        "ACCOUNT_REDIS_URL" to "redis://redis:6379",
        "ACCOUNT_SMTP_HOST" to "mail",
        "ACCOUNT_SMTP_FROM" to "no-reply@example.com",
        "ACCOUNT_S3_ENDPOINT" to "http://s3:9000",
        "ACCOUNT_S3_ACCESS_KEY" to "access",
        "ACCOUNT_S3_SECRET_KEY" to "secret",
        "ACCOUNT_JWT_PRIVATE_KEY" to "private",
        "ACCOUNT_JWT_PUBLIC_KEY" to "public",
        "ACCOUNT_JWT_ACTIVE_KID" to "key-1",
        "ACCOUNT_SECURITY_OTP_HMAC_SECRET" to "otp-secret-at-least-32-characters",
        "ACCOUNT_SECURITY_INTERNAL_AUTH_SECRET" to "internal-secret-at-least-32-characters",
        "ACCOUNT_GRPC_ENABLED" to "false"
    )

    @Test
    fun `loads role override and normalized settings`() {
        val config = EnvConfig.load(validEnv(), "worker")
        assertEquals("worker", config.property("account.runtime.role").getString())
        assertEquals("key-1", config.property("identity.jwt.active_kid").getString())
    }

    @Test
    fun `rejects direct and file secret together`() {
        val env = validEnv().apply { put("ACCOUNT_DATABASE_PASSWORD_FILE", "/tmp/password") }
        assertFailsWith<IllegalArgumentException> { EnvConfig.load(env) }
    }

    @Test
    fun `production grpc requires mtls`() {
        val env = validEnv().apply {
            put("ACCOUNT_ENV", "production")
            put("ACCOUNT_HTTP_COOKIE_SECURE", "true")
            put("ACCOUNT_SMTP_STARTTLS", "true")
            put("ACCOUNT_S3_PUBLIC_URL", "https://cdn.example.com/avatars")
            put("ACCOUNT_GRPC_ENABLED", "true")
        }
        assertFailsWith<IllegalArgumentException> { EnvConfig.load(env) }
    }
}
