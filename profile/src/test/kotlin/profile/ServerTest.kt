package profile

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.koin.ktor.ext.get
import profile.auth.ConfirmRegistrationRequest
import profile.auth.ForgotPasswordRequest
import profile.auth.LoginRequest
import profile.auth.RegisterRequest
import profile.auth.ResetPasswordRequest
import profile.infrastructure.db.VerificationTokenRepository
import profile.infrastructure.redis.PendingRegistrationStore
import profile.infrastructure.security.TokenHasher
import kotlin.test.*

class ServerTest {

    private fun TestApplicationBuilder.setupTestConfig() {
        environment {
            config = MapApplicationConfig(
                "identity.jwt.issuer" to "identity-service",
                "identity.jwt.audience" to "gateway",
                "identity.jwt.secret" to "test-secret",
                "identity.jwt.access_token_exp_minutes" to "15",
                "identity.session.refresh_token_exp_days" to "30",
                "identity.registration.pending_ttl_seconds" to "3600",
                "identity.registration.allow_in_memory_fallback" to "true",
                "identity.background.enabled" to "false",
                "postgres.url" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "postgres.user" to "sa",
                "postgres.password" to "",
                "redis.url" to "redis://localhost:6379",
                "smtp.host" to "localhost",
                "smtp.port" to "2500",
                "smtp.from" to "test@sparrow.local",
                "s3.endpoint" to "http://localhost:9000",
                "s3.public_url" to "http://localhost:9000",
                "s3.access_key" to "minio",
                "s3.secret_key" to "minio",
                "s3.bucket" to "avatars"
            )
        }
    }

    @Test
    fun `test health endpoint`() = testApplication {
        setupTestConfig()
        application {
            module()
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("UP"))
    }

    @Test
    fun `test pending registration confirmation flow`() = testApplication {
        setupTestConfig()
        lateinit var pendingRegistrationStore: PendingRegistrationStore
        application {
            module()
            pendingRegistrationStore = get()
        }
        val registerResponse = client.post("/api/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("test@example.com", "testuser", "password123")))
        }
        
        assertEquals(HttpStatusCode.Accepted, registerResponse.status, "Registration failed: ${registerResponse.bodyAsText()}")
        val body = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
        assertEquals("test@example.com", body["email"]?.toString()?.replace("\"", ""))

        val loginBeforeConfirmation = client.post("/api/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest(identifier = "test@example.com", password = "password123")))
        }
        assertEquals(HttpStatusCode.BadRequest, loginBeforeConfirmation.status)

        val code = codeForPendingRegistration(pendingRegistrationStore, "test@example.com")
        val confirmResponse = client.post("/api/auth/confirm-registration") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(ConfirmRegistrationRequest("test@example.com", code)))
        }

        assertEquals(HttpStatusCode.Created, confirmResponse.status, "Confirmation failed: ${confirmResponse.bodyAsText()}")
        val confirmBody = Json.parseToJsonElement(confirmResponse.bodyAsText()).jsonObject
        assertNotNull(confirmBody["accessToken"], "Confirmation should auto-login and return an access token")
        val userBody = confirmBody["user"]!!.jsonObject
        assertEquals("true", userBody["emailVerified"].toString())
        val confirmCookie = confirmResponse.headers.getAll(HttpHeaders.SetCookie)?.find { it.startsWith("refresh_token=") }
        assertNotNull(confirmCookie, "Confirmation should set refresh token cookie")

        val reusedCodeResponse = client.post("/api/auth/confirm-registration") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(ConfirmRegistrationRequest("test@example.com", code)))
        }
        assertEquals(HttpStatusCode.BadRequest, reusedCodeResponse.status)
    }

    @Test
    fun `test login and session listing`() = testApplication {
        setupTestConfig()
        lateinit var pendingRegistrationStore: PendingRegistrationStore
        lateinit var verificationTokenRepository: VerificationTokenRepository
        application {
            module()
            pendingRegistrationStore = get()
            verificationTokenRepository = get()
        }
        
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // 1. Register + confirm
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("login@example.com", "loginuser", "password123"))
        }
        assertEquals(HttpStatusCode.Accepted, registerResponse.status, "Registration failed: ${registerResponse.bodyAsText()}")

        val code = codeForPendingRegistration(pendingRegistrationStore, "login@example.com")
        val confirmResponse = client.post("/api/auth/confirm-registration") {
            contentType(ContentType.Application.Json)
            setBody(ConfirmRegistrationRequest("login@example.com", code))
        }
        assertEquals(HttpStatusCode.Created, confirmResponse.status, "Confirmation failed: ${confirmResponse.bodyAsText()}")

        // 2. Login by username
        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(identifier = "loginuser", password = "password123", deviceId = "test-device"))
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status, "Login failed: ${loginResponse.bodyAsText()}")
        val loginBody = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
        val userId = loginBody["userId"]?.toString()?.replace("\"", "")
        assertNotNull(userId)

        // Check for refresh_token cookie
        val cookie = loginResponse.headers.getAll(HttpHeaders.SetCookie)?.find { it.startsWith("refresh_token=") }
        assertNotNull(cookie, "Refresh token cookie not found in response")
        assertTrue(cookie.contains("HttpOnly"), "Cookie should be HttpOnly")
        assertTrue(cookie.contains("SameSite=Lax"), "Cookie should set SameSite=Lax")
        assertTrue(cookie.contains("Path=/api/auth"), "Cookie should be scoped to /api/auth")

        val accessToken = loginBody["accessToken"]?.toString()?.replace("\"", "")
        assertNotNull(accessToken)

        // 3. Get Sessions (using Bearer token)
        val sessionsResponse = client.get("/api/sessions") {
            bearerAuth(accessToken!!)
        }

        assertEquals(HttpStatusCode.OK, sessionsResponse.status)
        assertTrue(sessionsResponse.bodyAsText().contains("\"id\""), "Sessions should expose id")
        assertFalse(sessionsResponse.bodyAsText().contains("sessionId"), "Sessions should not expose sessionId")
        assertTrue(sessionsResponse.bodyAsText().contains("loginuser"), "Sessions list should contain the user")
        assertTrue(sessionsResponse.bodyAsText().contains("test-device"), "Sessions list should contain the device ID")

        // 4. Refresh
        val refreshResponse = client.post("/api/auth/refresh") {
            header(HttpHeaders.Cookie, cookie.substringBefore(";"))
        }
        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val refreshBody = Json.parseToJsonElement(refreshResponse.bodyAsText()).jsonObject
        assertNotNull(refreshBody["accessToken"])
        val rotatedCookie = refreshResponse.headers.getAll(HttpHeaders.SetCookie)?.find { it.startsWith("refresh_token=") }
        assertNotNull(rotatedCookie, "Rotated refresh token cookie not found in response")
        assertNotEquals(cookie.substringBefore(";"), rotatedCookie.substringBefore(";"), "Refresh token should rotate")

        val oldRefreshResponse = client.post("/api/auth/refresh") {
            header(HttpHeaders.Cookie, cookie.substringBefore(";"))
        }
        assertEquals(HttpStatusCode.BadRequest, oldRefreshResponse.status)

        // 5. Reset password using a 6-digit code and login with the new password.
        val forgotResponse = client.post("/api/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(identifier = "loginuser"))
        }
        assertEquals(HttpStatusCode.OK, forgotResponse.status)

        val resetCode = codeForVerificationToken(verificationTokenRepository, "PASSWORD_RESET")
        val resetResponse = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(identifier = "loginuser", code = resetCode, newPassword = "newpassword123"))
        }
        assertEquals(HttpStatusCode.OK, resetResponse.status, "Password reset failed: ${resetResponse.bodyAsText()}")

        val reloginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(identifier = "login@example.com", password = "newpassword123"))
        }
        assertEquals(HttpStatusCode.OK, reloginResponse.status, "Login with reset password failed: ${reloginResponse.bodyAsText()}")
    }

    private fun codeForPendingRegistration(store: PendingRegistrationStore, email: String): String {
        val pending = store.findByEmail(email) ?: error("Pending registration missing for $email")
        for (value in 0..999_999) {
            val code = value.toString().padStart(6, '0')
            if (TokenHasher.hash(code) == pending.codeHash) return code
        }
        error("Could not resolve pending registration code")
    }

    private fun codeForVerificationToken(repository: VerificationTokenRepository, purpose: String): String {
        for (value in 0..999_999) {
            val code = value.toString().padStart(6, '0')
            val token = repository.findByHash(TokenHasher.hash(code))
            if (token?.purpose == purpose) return code
        }
        error("Could not resolve $purpose verification code")
    }
}
