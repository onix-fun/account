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
import profile.infrastructure.db.UserRepository
import profile.infrastructure.redis.PendingRegistrationStore
import profile.infrastructure.security.TokenHasher
import profile.users.UpdateProfileRequest
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.*

class ServerTest {

    private fun TestApplicationBuilder.setupTestConfig(cookieDomain: String? = null, cookieSecure: Boolean = false) {
        environment {
            config = MapApplicationConfig(
                "identity.jwt.issuer" to "account-service",
                "identity.jwt.audience" to "account",
                "identity.jwt.private_key_path" to testPrivateKeyPath,
                "identity.jwt.public_key_path" to testPublicKeyPath,
                "identity.jwt.access_token_exp_minutes" to "15",
                "identity.session.refresh_token_exp_days" to "30",
                "identity.session.cookie_secure" to cookieSecure.toString(),
                "identity.session.cookie_domain" to cookieDomain.orEmpty(),
                "identity.registration.pending_ttl_seconds" to "3600",
                "identity.registration.allow_in_memory_fallback" to "true",
                "identity.background.enabled" to "false",
                "postgres.url" to "jdbc:h2:mem:test-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "postgres.user" to "sa",
                "postgres.password" to "",
                "redis.url" to "redis://localhost:6379",
                "smtp.host" to "localhost",
                "smtp.port" to "2500",
                "smtp.from" to "test@account.local",
                "s3.endpoint" to "http://localhost:9000",
                "s3.public_url" to "http://localhost:9000",
                "s3.access_key" to "minio",
                "s3.secret_key" to "minio",
                "s3.bucket" to "avatars",
                "identity.security.otp_hmac_secret" to "test-otp-hmac-secret-at-least-32-characters!",
                "identity.security.internal_auth_secret" to "test-internal-auth-secret-at-least-32-characters"
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
        setupTestConfig(cookieDomain = "example.com", cookieSecure = true)
        lateinit var pendingRegistrationStore: PendingRegistrationStore
        lateinit var verificationTokenRepository: VerificationTokenRepository
        lateinit var userRepository: UserRepository
        application {
            module()
            pendingRegistrationStore = get()
            verificationTokenRepository = get()
            userRepository = get()
        }
        val registerResponse = client.post("/api/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("test@example.com", "testuser", "password123")))
        }
        
        assertEquals(HttpStatusCode.Accepted, registerResponse.status, "Registration failed: ${registerResponse.bodyAsText()}")
        val body = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
        assertEquals("\"CODE_SENT\"", body["status"].toString())
        assertEquals(null, body["email"])
        val unavailableResponse = client.get("/api/auth/username-available?username=testuser")
        assertEquals(HttpStatusCode.OK, unavailableResponse.status)
        assertTrue(unavailableResponse.bodyAsText().contains("\"available\":false"))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/search/search?q=te").status)

        val loginBeforeConfirmation = client.post("/api/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest(identifier = "test@example.com", password = "password123")))
        }
        assertEquals(HttpStatusCode.Unauthorized, loginBeforeConfirmation.status)
        val loginError = Json.parseToJsonElement(loginBeforeConfirmation.bodyAsText()).jsonObject
        assertEquals("\"AUTH_INVALID_CREDENTIALS\"", loginError["code"].toString())
        assertNotNull(loginError["numericCode"])
        assertNotNull(loginError["fieldErrors"])
        assertNull(loginError["requestId"])

        val pendingLookup = client.get("/api/auth/account-lookup?identifier=testuser")
        assertEquals(HttpStatusCode.OK, pendingLookup.status)
        assertTrue(pendingLookup.bodyAsText().contains("\"state\":\"PENDING_REGISTRATION\""))

        val missingLookup = client.get("/api/auth/account-lookup?identifier=missing-user")
        assertEquals(HttpStatusCode.OK, missingLookup.status)
        assertTrue(missingLookup.bodyAsText().contains("\"state\":\"NOT_FOUND\""))

        val missingEmailLookup = client.get("/api/auth/account-lookup?identifier=missing@example.com")
        assertEquals(HttpStatusCode.OK, missingEmailLookup.status)
        assertTrue(missingEmailLookup.bodyAsText().contains("\"state\":\"NOT_FOUND\""))

        val code = codeForPendingRegistration(pendingRegistrationStore, "test@example.com")
        val confirmResponse = client.post("/api/auth/confirm-registration") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(ConfirmRegistrationRequest("test@example.com", code)))
        }

        assertEquals(HttpStatusCode.Created, confirmResponse.status, "Confirmation failed: ${confirmResponse.bodyAsText()}")
        val confirmBody = Json.parseToJsonElement(confirmResponse.bodyAsText()).jsonObject
        assertNull(confirmBody["accessToken"], "Browser confirmation must not expose an access token")
        val userBody = confirmBody["user"]!!.jsonObject
        assertEquals("true", userBody["emailVerified"].toString())
        val userId = userBody["id"].toString().replace("\"", "")
        val confirmCookie = cookieWithPrefix(confirmResponse, "refresh_token_$userId=")
        assertNotNull(confirmCookie, "Confirmation should set refresh token cookie")
        assertTrue(confirmCookie.contains("Domain=example.com"))
        assertTrue(confirmCookie.contains("Secure"))
        assertNotNull(cookieWithPrefix(confirmResponse, "__Host-access_token="), "Confirmation should set access token cookie")
        assertNotNull(cookieWithPrefix(confirmResponse, "__Host-active_user="), "Confirmation should set active account cookie")

        val activeLookup = client.get("/api/auth/account-lookup?identifier=test@example.com")
        assertEquals(HttpStatusCode.OK, activeLookup.status)
        assertTrue(activeLookup.bodyAsText().contains("\"state\":\"EMAIL_LOGIN\""))
        assertFalse(activeLookup.bodyAsText().contains("\"username\""))
        assertFalse(activeLookup.bodyAsText().contains("\"email\""))

        userRepository.updateEmailVerified(userId, false)
        val unverifiedLookup = client.get("/api/auth/account-lookup?identifier=testuser")
        assertTrue(unverifiedLookup.bodyAsText().contains("\"state\":\"EMAIL_UNVERIFIED\""))
        val requestVerification = client.post("/api/auth/public-verification/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"identifier":"testuser"}""")
        }
        assertEquals(HttpStatusCode.OK, requestVerification.status)
        val verificationCode = codeForVerificationToken(verificationTokenRepository, "EMAIL_VERIFICATION")
        val confirmVerification = client.post("/api/auth/public-verification/confirm") {
            contentType(ContentType.Application.Json)
            setBody("""{"identifier":"testuser","code":"$verificationCode"}""")
        }
        assertEquals(HttpStatusCode.OK, confirmVerification.status)
        assertTrue(client.get("/api/auth/account-lookup?identifier=testuser").bodyAsText().contains("\"state\":\"ACTIVE\""))

        val reusedCodeResponse = client.post("/api/auth/confirm-registration") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(Json.encodeToString(ConfirmRegistrationRequest("test@example.com", code)))
        }
        assertEquals(HttpStatusCode.NotFound, reusedCodeResponse.status)
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
        val userId = loginBody["user"]?.jsonObject?.get("id")?.toString()?.replace("\"", "")
        assertNotNull(userId)

        assertNull(loginBody["accessToken"], "Browser login must not expose an access token")

        // Check browser session cookies.
        val cookie = cookieWithPrefix(loginResponse, "refresh_token_$userId=")
        assertNotNull(cookie, "Refresh token cookie not found in response")
        assertTrue(cookie.contains("HttpOnly"), "Cookie should be HttpOnly")
        assertTrue(cookie.contains("SameSite=Strict"), "Cookie should set SameSite=Strict")
        assertTrue(cookie.contains("Path=/api/auth"), "Cookie should be scoped to /api/auth")
        assertFalse(cookie.contains("Domain="), "Development cookie should stay host-only")
        val accessCookie = cookieWithPrefix(loginResponse, "__Host-access_token=")
        assertNotNull(accessCookie, "Login should set access token cookie")
        assertTrue(accessCookie.contains("HttpOnly"), "Access cookie should be HttpOnly")
        assertTrue(accessCookie.contains("SameSite=Strict"), "Access cookie should set SameSite=Strict")

        val activeCookie = cookieWithPrefix(loginResponse, "__Host-active_user=")
        assertNotNull(activeCookie, "Active account cookie not found in response")

        // 3. Get sessions using the HttpOnly-style browser access cookie.
        val sessionsResponse = client.get("/api/sessions") {
            header(HttpHeaders.Cookie, cookiePair(accessCookie))
        }

        assertEquals(HttpStatusCode.OK, sessionsResponse.status)
        assertTrue(sessionsResponse.bodyAsText().contains("\"id\""), "Sessions should expose id")
        assertFalse(sessionsResponse.bodyAsText().contains("sessionId"), "Sessions should not expose sessionId")
        assertTrue(sessionsResponse.bodyAsText().contains("loginuser"), "Sessions list should contain the user")
        assertTrue(sessionsResponse.bodyAsText().contains("test-device"), "Sessions list should contain the device ID")

        // 4. Refresh
        val refreshResponse = client.post("/api/auth/refresh") {
            header(HttpHeaders.Cookie, listOf(cookiePair(cookie), cookiePair(activeCookie)).joinToString("; "))
        }
        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val refreshBody = Json.parseToJsonElement(refreshResponse.bodyAsText()).jsonObject
        assertNull(refreshBody["accessToken"], "Browser refresh must not expose an access token")
        assertNotNull(refreshBody["user"])
        val rotatedCookie = cookieWithPrefix(refreshResponse, "refresh_token_$userId=")
        assertNotNull(rotatedCookie, "Rotated refresh token cookie not found in response")
        assertNotEquals(cookiePair(cookie), cookiePair(rotatedCookie), "Refresh token should rotate")

        val oldRefreshResponse = client.post("/api/auth/refresh") {
            header(HttpHeaders.Cookie, listOf(cookiePair(cookie), cookiePair(activeCookie)).joinToString("; "))
        }
        assertEquals(HttpStatusCode.OK, oldRefreshResponse.status)
        assertNotNull(
            cookieWithPrefix(oldRefreshResponse, "refresh_token_$userId="),
            "Browser refresh should recover when a concurrent request reused the previous cookie"
        )

        // 5. API clients receive a distinct Bearer token pair and rotate opaque refresh tokens.
        val apiTokenResponse = client.post("/api/auth/token") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(identifier = "loginuser", password = "password123"))
        }
        assertEquals(HttpStatusCode.OK, apiTokenResponse.status)
        val apiTokenBody = Json.parseToJsonElement(apiTokenResponse.bodyAsText()).jsonObject
        val apiAccessToken = apiTokenBody["accessToken"]?.toString()?.replace("\"", "")
        val apiRefreshToken = apiTokenBody["refreshToken"]?.toString()?.replace("\"", "")
        assertNotNull(apiAccessToken)
        assertNotNull(apiRefreshToken)

        val bearerSessionsResponse = client.get("/api/sessions") {
            bearerAuth(apiAccessToken)
        }
        assertEquals(HttpStatusCode.OK, bearerSessionsResponse.status)

        val renamedResponse = client.patch("/api/users/me") {
            contentType(ContentType.Application.Json)
            bearerAuth(apiAccessToken)
            setBody(UpdateProfileRequest(username = "renameduser"))
        }
        assertEquals(HttpStatusCode.OK, renamedResponse.status)
        assertTrue(renamedResponse.bodyAsText().contains("\"username\":\"renameduser\""))
        assertTrue(client.get("/api/auth/username-available?username=LOGINUSER").bodyAsText().contains("\"available\":true"))
        assertTrue(client.get("/api/auth/username-available?username=RENAMEDUSER").bodyAsText().contains("\"available\":false"))

        val unchangedUsernameResponse = client.patch("/api/users/me") {
            contentType(ContentType.Application.Json)
            bearerAuth(apiAccessToken)
            setBody(UpdateProfileRequest(username = "renameduser", firstName = "Renamed"))
        }
        assertEquals(HttpStatusCode.OK, unchangedUsernameResponse.status)

        val secondRegisterResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("taken@example.com", "takenuser", "password123"))
        }
        assertEquals(HttpStatusCode.Accepted, secondRegisterResponse.status)
        val secondCode = codeForPendingRegistration(pendingRegistrationStore, "taken@example.com")
        assertEquals(HttpStatusCode.Created, client.post("/api/auth/confirm-registration") {
            contentType(ContentType.Application.Json)
            setBody(ConfirmRegistrationRequest("taken@example.com", secondCode))
        }.status)

        val takenUsernameResponse = client.patch("/api/users/me") {
            contentType(ContentType.Application.Json)
            bearerAuth(apiAccessToken)
            setBody(UpdateProfileRequest(username = "TAKENUSER"))
        }
        assertEquals(HttpStatusCode.Conflict, takenUsernameResponse.status)
        assertTrue(takenUsernameResponse.bodyAsText().contains("\"code\":\"AUTH_USERNAME_IN_USE\""))
        assertTrue(takenUsernameResponse.bodyAsText().contains("\"field\":\"username\""))

        val apiRefreshResponse = client.post("/api/auth/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$apiRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.OK, apiRefreshResponse.status)
        val rotatedApiRefresh = Json.parseToJsonElement(apiRefreshResponse.bodyAsText())
            .jsonObject["refreshToken"]?.toString()?.replace("\"", "")
        assertNotNull(rotatedApiRefresh)
        assertNotEquals(apiRefreshToken, rotatedApiRefresh)

        val reusedApiRefreshResponse = client.post("/api/auth/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$apiRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, reusedApiRefreshResponse.status)

        val csrfResponse = client.get("/api/auth/csrf")
        assertEquals(HttpStatusCode.OK, csrfResponse.status)
        assertNotNull(Json.parseToJsonElement(csrfResponse.bodyAsText()).jsonObject["csrfToken"])
        val csrfCookie = cookieWithPrefix(csrfResponse, "__Host-csrf_token=")
        assertNotNull(csrfCookie)
        assertTrue(csrfCookie.contains("HttpOnly"))
        assertTrue(csrfCookie.contains("SameSite=Strict"))

        // 6. Reset password using a 6-digit code and login with the new password.
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

    @Test
    fun `test browser account listing and switch`() = testApplication {
        setupTestConfig()
        lateinit var pendingRegistrationStore: PendingRegistrationStore
        application {
            module()
            pendingRegistrationStore = get()
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        suspend fun confirmAccount(email: String, username: String): HttpResponse {
            val registerResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email, username, "password123"))
            }
            assertEquals(HttpStatusCode.Accepted, registerResponse.status)
            val code = codeForPendingRegistration(pendingRegistrationStore, email)
            return client.post("/api/auth/confirm-registration") {
                contentType(ContentType.Application.Json)
                setBody(ConfirmRegistrationRequest(email, code))
            }
        }

        val firstResponse = confirmAccount("first@example.com", "firstuser")
        val secondResponse = confirmAccount("second@example.com", "seconduser")
        val firstId = Json.parseToJsonElement(firstResponse.bodyAsText()).jsonObject["user"]!!
            .jsonObject["id"].toString().replace("\"", "")
        val secondId = Json.parseToJsonElement(secondResponse.bodyAsText()).jsonObject["user"]!!
            .jsonObject["id"].toString().replace("\"", "")
        val firstRefresh = cookieWithPrefix(firstResponse, "refresh_token_$firstId=")
        val secondRefresh = cookieWithPrefix(secondResponse, "refresh_token_$secondId=")
        assertNotNull(firstRefresh)
        assertNotNull(secondRefresh)
        val accountCookies = listOf(cookiePair(firstRefresh), cookiePair(secondRefresh)).joinToString("; ")

        val accountsResponse = client.get("/api/auth/accounts") {
            header(HttpHeaders.Cookie, accountCookies)
        }
        assertEquals(HttpStatusCode.OK, accountsResponse.status)
        assertTrue(accountsResponse.bodyAsText().contains("firstuser"))
        assertTrue(accountsResponse.bodyAsText().contains("seconduser"))

        val mismatchedRefreshResponse = client.post("/api/auth/refresh") {
            header(
                HttpHeaders.Cookie,
                "refresh_token_$secondId=${cookiePair(firstRefresh).substringAfter("=")}; __Host-active_user=$secondId"
            )
        }
        assertEquals(HttpStatusCode.NotFound, mismatchedRefreshResponse.status)

        val switchResponse = client.post("/api/auth/switch") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Cookie, accountCookies)
            setBody("""{"userId":"$firstId"}""")
        }
        assertEquals(HttpStatusCode.OK, switchResponse.status)
        val activeUserId = Json.parseToJsonElement(switchResponse.bodyAsText()).jsonObject["user"]!!
            .jsonObject["id"].toString().replace("\"", "")
        assertEquals(firstId, activeUserId)
        val rotatedFirstRefresh = cookieWithPrefix(switchResponse, "refresh_token_$firstId=")!!
        assertNotEquals(cookiePair(firstRefresh), cookiePair(rotatedFirstRefresh))
        assertNotNull(cookieWithPrefix(switchResponse, "__Host-access_token="))
        val firstActiveCookie = cookieWithPrefix(switchResponse, "__Host-active_user=")
        assertNotNull(firstActiveCookie)

        val logoutResponse = client.post("/api/auth/logout") {
            header(
                HttpHeaders.Cookie,
                listOf(cookiePair(rotatedFirstRefresh), cookiePair(firstActiveCookie)).joinToString("; ")
            )
        }
        assertEquals(HttpStatusCode.OK, logoutResponse.status)
        assertTrue(cookieWithPrefix(logoutResponse, "refresh_token_$firstId=")!!.contains("Max-Age=0"))
        assertTrue(cookieWithPrefix(logoutResponse, "__Host-access_token=")!!.contains("Max-Age=0"))
        assertTrue(cookieWithPrefix(logoutResponse, "__Host-active_user=")!!.contains("Max-Age=0"))

        val accountsAfterLogoutResponse = client.get("/api/auth/accounts") {
            header(
                HttpHeaders.Cookie,
                listOf(cookiePair(rotatedFirstRefresh), cookiePair(secondRefresh)).joinToString("; ")
            )
        }
        assertEquals(HttpStatusCode.OK, accountsAfterLogoutResponse.status)
        assertFalse(accountsAfterLogoutResponse.bodyAsText().contains("firstuser"))
        assertTrue(accountsAfterLogoutResponse.bodyAsText().contains("seconduser"))

        val switchToSecondResponse = client.post("/api/auth/switch") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Cookie, cookiePair(secondRefresh))
            setBody("""{"userId":"$secondId"}""")
        }
        assertEquals(HttpStatusCode.OK, switchToSecondResponse.status)
    }

    private fun cookieWithPrefix(response: HttpResponse, prefix: String): String? {
        return response.headers.getAll(HttpHeaders.SetCookie)?.find { it.startsWith(prefix) }
    }

    private fun cookiePair(cookie: String): String = cookie.substringBefore(";")

    private fun codeForPendingRegistration(store: PendingRegistrationStore, email: String): String {
        val pending = store.findByEmail(email) ?: error("Pending registration missing for $email")
        for (value in 0..999_999) {
            val code = value.toString().padStart(6, '0')
            if (TokenHasher.challenge("development-only-otp-secret", "REGISTRATION", email, code) == pending.codeHash) return code
        }
        error("Could not resolve pending registration code")
    }

    private fun codeForVerificationToken(repository: VerificationTokenRepository, purpose: String): String {
        for (value in 0..999_999) {
            val code = value.toString().padStart(6, '0')
            val token = repository.findByHash(TokenHasher.challenge("development-only-otp-secret", purpose, testUserIdForPurpose(repository, purpose), code))
            if (token?.purpose == purpose) return code
        }
        error("Could not resolve $purpose verification code")
    }

    private fun testUserIdForPurpose(repository: VerificationTokenRepository, purpose: String): String {
        val field = VerificationTokenRepository::class.java.getDeclaredField("dataSource").apply { isAccessible = true }
        val dataSource = field.get(repository) as javax.sql.DataSource
        return dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT user_id FROM verification_tokens WHERE purpose=? AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1").use {
                it.setString(1, purpose); val rs = it.executeQuery(); if (rs.next()) rs.getObject(1).toString() else error("Missing token")
            }
        }
    }

    private companion object {
        private val testKeyPaths by lazy {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val keyPair = generator.generateKeyPair()
            val directory = Files.createTempDirectory("account-account-test-keys")
            val privatePath = directory.resolve("private.pem")
            val publicPath = directory.resolve("public.pem")
            Files.writeString(privatePath, pem("PRIVATE KEY", keyPair.private.encoded))
            Files.writeString(publicPath, pem("PUBLIC KEY", keyPair.public.encoded))
            privatePath.toString() to publicPath.toString()
        }

        private val testPrivateKeyPath: String get() = testKeyPaths.first
        private val testPublicKeyPath: String get() = testKeyPaths.second

        private fun pem(type: String, bytes: ByteArray): String {
            val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes)
            return "-----BEGIN $type-----\n$encoded\n-----END $type-----\n"
        }
    }
}
