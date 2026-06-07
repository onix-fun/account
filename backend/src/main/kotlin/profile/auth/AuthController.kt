package profile.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import profile.infrastructure.config.JwtConfig
import profile.infrastructure.events.EmailLocale
import profile.shared.ApiErrorCode
import profile.shared.apiError
import profile.users.toProfileDto
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class AuthController(
    private val authService: AuthService,
    private val sessionConfig: profile.infrastructure.config.SessionConfig,
    private val jwtConfig: JwtConfig
) {
    private val random = SecureRandom()

    suspend fun register(call: ApplicationCall) {
        val request = call.receive<RegisterRequest>()
        val response = authService.register(
            request.email,
            request.username,
            request.password,
            request.firstName,
            request.lastName,
            call.emailLocale()
        )
        call.respond(HttpStatusCode.Accepted, response)
    }

    suspend fun confirmRegistration(call: ApplicationCall) {
        val request = call.receive<ConfirmRegistrationRequest>()
        val userAgent = call.request.headers["User-Agent"]
        val ipAddress = call.clientIpAddress()
        val result =
            authService.confirmRegistration(request.identifier ?: request.email.orEmpty(), request.code, request.deviceId, userAgent, ipAddress)
        appendBrowserSession(call, result)
        call.respond(HttpStatusCode.Created, BrowserAuthResponse(result.user.toProfileDto()))
    }

    suspend fun resendRegistrationCode(call: ApplicationCall) {
        val request = call.receive<ResendRegistrationCodeRequest>()
        val response = authService.resendRegistrationCode(request.identifier ?: request.email.orEmpty(), call.emailLocale())
        call.respond(HttpStatusCode.OK, response)
    }

    suspend fun login(call: ApplicationCall) {
        val request = call.receive<LoginRequest>()
        val userAgent = call.request.headers["User-Agent"]
        val ipAddress = call.clientIpAddress()

        val result = authService.login(
            request.identifier ?: request.email.orEmpty(),
            request.password,
            request.deviceId,
            userAgent,
            ipAddress
        )

        appendBrowserSession(call, result)
        call.respond(HttpStatusCode.OK, BrowserAuthResponse(result.user.toProfileDto()))
    }

    suspend fun token(call: ApplicationCall) {
        val request = call.receive<LoginRequest>()
        val result = authService.login(
            request.identifier ?: request.email.orEmpty(),
            request.password,
            request.deviceId,
            call.request.headers["User-Agent"],
            call.clientIpAddress()
        )
        call.respond(HttpStatusCode.OK, apiTokenResponse(result.accessToken, result.refreshToken))
    }

    suspend fun tokenRefresh(call: ApplicationCall) {
        val request = call.receive<TokenRefreshRequest>()
        val result = authService.refresh(request.refreshToken)
        call.respond(HttpStatusCode.OK, apiTokenResponse(result.accessToken, result.refreshToken))
    }

    suspend fun csrf(call: ApplicationCall) {
        val token = generateOpaqueToken()
        call.response.cookies.append(csrfCookie(token))
        call.respond(HttpStatusCode.OK, CsrfResponse(token))
    }

    suspend fun usernameAvailable(call: ApplicationCall) {
        val username = call.request.queryParameters["username"].orEmpty()
        call.respond(HttpStatusCode.OK, UsernameAvailabilityResponse(authService.isUsernameAvailable(username)))
    }

    suspend fun accountLookup(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, authService.lookupAccount(call.request.queryParameters["identifier"].orEmpty()))
    }

    suspend fun requestPublicVerification(call: ApplicationCall) {
        val request = call.receive<PublicVerificationRequest>()
        authService.requestPublicEmailVerification(request.identifier, call.emailLocale())
        call.respond(HttpStatusCode.OK)
    }

    suspend fun confirmPublicVerification(call: ApplicationCall) {
        val request = call.receive<PublicVerificationConfirmRequest>()
        authService.confirmPublicEmailVerification(request.identifier, request.code)
        call.respond(HttpStatusCode.OK)
    }

    suspend fun accounts(call: ApplicationCall) {
        val accounts = requestRefreshTokens(call)
            .mapNotNull { (userId, token) ->
                authService.accountForRefreshToken(token)?.takeIf { it.id == userId }
            }
            .distinctBy { it.id }
            .map { it.toBrowserAccountDto() }
        call.respond(HttpStatusCode.OK, accounts)
    }

    suspend fun switchAccount(call: ApplicationCall) {
        val request = call.receive<SwitchAccountRequest>()
        val userId = requireUserId(request.userId)
        val refreshToken = call.request.cookies[getRefreshCookieName(userId)]
            ?: apiError(ApiErrorCode.AUTH_SESSION_NOT_FOUND)
        val result = refreshBrowserAccount(refreshToken, userId)
        appendBrowserRefresh(call, result)
        call.respond(HttpStatusCode.OK, BrowserAuthResponse(result.user.toProfileDto()))
    }

    suspend fun verifyEmail(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val request = call.receive<VerifyEmailRequest>()
        authService.verifyEmail(userId, request.code)
        call.respond(HttpStatusCode.OK, mapOf("message" to "Email verified successfully"))
    }

    suspend fun resendVerification(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        authService.requestEmailVerification(userId, call.emailLocale())
        call.respond(HttpStatusCode.OK, mapOf("message" to "Verification code resent"))
    }

    suspend fun forgotPassword(call: ApplicationCall) {
        val request = call.receive<ForgotPasswordRequest>()
        authService.forgotPassword(request.identifier ?: request.email.orEmpty(), call.emailLocale())
        call.respond(HttpStatusCode.OK, mapOf("message" to "If the account exists, a reset code has been sent"))
    }

    suspend fun resetPassword(call: ApplicationCall) {
        val request = call.receive<ResetPasswordRequest>()
        authService.resetPassword(
            request.identifier ?: request.email.orEmpty(),
            request.code ?: request.token.orEmpty(),
            request.newPassword,
            call.emailLocale()
        )
        call.respond(HttpStatusCode.OK, mapOf("message" to "Password has been reset successfully"))
    }

    suspend fun changePassword(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val request = call.receive<ChangePasswordRequest>()
        authService.changePassword(userId, request.currentPassword, request.newPassword, call.emailLocale())

        requestRefreshTokens(call)
            .keys
            .filter { it == userId }
            .forEach { call.response.cookies.append(clearRefreshCookie(it)) }
        clearActiveBrowserSession(call)

        call.respond(HttpStatusCode.OK, mapOf("message" to "Password has been changed successfully"))
    }

    suspend fun deleteAccount(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val request = call.receive<DeleteAccountRequest>()
        authService.deleteAccount(userId, request.password)

        requestRefreshTokens(call)
            .keys
            .filter { it == userId }
            .forEach { call.response.cookies.append(clearRefreshCookie(it)) }
        clearActiveBrowserSession(call)

        call.respond(HttpStatusCode.OK, mapOf("message" to "Account has been deleted"))
    }

    suspend fun refresh(call: ApplicationCall) {
        val browserAccount = resolveBrowserAccount(call)
        if (browserAccount == null) {
            apiError(ApiErrorCode.AUTH_SESSION_NOT_FOUND)
        }

        val (activeUserId, refreshToken) = browserAccount
        val result = refreshBrowserAccount(refreshToken, activeUserId)
        appendBrowserRefresh(call, result)
        call.respond(HttpStatusCode.OK, BrowserAuthResponse(result.user.toProfileDto()))
    }

    suspend fun logout(call: ApplicationCall) {
        val userId = activeUserId(call)
        val refreshToken = userId?.let { call.request.cookies[getRefreshCookieName(it)] }
        if (refreshToken != null) {
            authService.logout(refreshToken)
        }

        if (userId != null) call.response.cookies.append(clearRefreshCookie(userId))
        clearActiveBrowserSession(call)

        call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
    }

    suspend fun logoutAll(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()?.payload?.subject
        if (userId == null) {
            apiError(ApiErrorCode.AUTH_UNAUTHORIZED)
        }

        authService.logoutAll(userId)

        requestRefreshTokens(call)
            .keys
            .filter { it == userId }
            .forEach { call.response.cookies.append(clearRefreshCookie(it)) }
        clearActiveBrowserSession(call)

        call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out from all devices"))
    }

    private fun appendBrowserSession(call: ApplicationCall, result: LoginResult) {
        call.response.cookies.append(refreshCookie(result.user.id, result.refreshToken))
        call.response.cookies.append(accessCookie(result.accessToken))
        call.response.cookies.append(activeUserCookie(result.user.id))
    }

    private fun appendBrowserRefresh(call: ApplicationCall, result: RefreshResult) {
        call.response.cookies.append(refreshCookie(result.user.id, result.refreshToken))
        call.response.cookies.append(accessCookie(result.accessToken))
        call.response.cookies.append(activeUserCookie(result.user.id))
    }

    private fun clearActiveBrowserSession(call: ApplicationCall) {
        call.response.cookies.append(clearAccessCookie())
        call.response.cookies.append(clearActiveUserCookie())
    }

    private fun getRefreshCookieName(userId: String): String {
        return "${REFRESH_COOKIE_PREFIX}$userId"
    }

    private fun refreshBrowserAccount(refreshToken: String, userId: String): RefreshResult {
        val account = authService.accountForRefreshToken(refreshToken, allowPreviousToken = true)
        if (account?.id != userId) apiError(ApiErrorCode.AUTH_INVALID_REFRESH_TOKEN)
        return authService.refresh(refreshToken, allowPreviousToken = true)
    }

    private fun resolveBrowserAccount(call: ApplicationCall): Pair<String, String>? {
        val refreshTokens = requestRefreshTokens(call)
        val preferredIds = listOfNotNull(
            activeUserId(call)
        )

        preferredIds.forEach { userId ->
            refreshTokens[userId]?.let { token ->
                if (authService.accountForRefreshToken(token, allowPreviousToken = true)?.id == userId) {
                    return userId to token
                }
            }
        }

        refreshTokens.forEach { (userId, token) ->
            if (authService.accountForRefreshToken(token, allowPreviousToken = true)?.id == userId) {
                return userId to token
            }
        }

        return null
    }

    private fun requireUserId(value: String): String {
        return parseUserId(value) ?: apiError(ApiErrorCode.VALIDATION_INVALID_UUID, "userId")
    }

    private fun parseUserId(value: String): String? {
        return runCatching { UUID.fromString(value).toString() }.getOrNull()
    }

    private fun refreshCookie(userId: String, value: String): Cookie {
        return Cookie(
            name = getRefreshCookieName(userId),
            value = value,
            httpOnly = true,
            secure = sessionConfig.cookieSecure,
            domain = sessionConfig.cookieDomain,
            path = REFRESH_COOKIE_PATH,
            maxAge = refreshCookieMaxAgeSeconds(),
            extensions = mapOf("SameSite" to "Strict")
        )
    }

    private fun clearRefreshCookie(userId: String): Cookie {
        return Cookie(
            name = getRefreshCookieName(userId),
            value = "",
            httpOnly = true,
            secure = sessionConfig.cookieSecure,
            domain = sessionConfig.cookieDomain,
            path = REFRESH_COOKIE_PATH,
            maxAge = 0,
            extensions = mapOf("SameSite" to "Strict")
        )
    }

    private fun accessCookie(value: String) = browserCookie(
        name = browserCookieName(ACCESS_COOKIE_NAME),
        value = value,
        maxAge = accessCookieMaxAgeSeconds(),
        sameSite = "Strict"
    )

    private fun clearAccessCookie() = browserCookie(name = browserCookieName(ACCESS_COOKIE_NAME), value = "", maxAge = 0, sameSite = "Strict")

    private fun activeUserCookie(userId: String) = browserCookie(
        name = browserCookieName(ACTIVE_USER_COOKIE_NAME),
        value = userId,
        maxAge = refreshCookieMaxAgeSeconds()
    )

    private fun clearActiveUserCookie() = browserCookie(name = browserCookieName(ACTIVE_USER_COOKIE_NAME), value = "", maxAge = 0)

    private fun csrfCookie(value: String) = browserCookie(
        name = browserCookieName(CSRF_COOKIE_NAME),
        value = value,
        maxAge = refreshCookieMaxAgeSeconds(),
        sameSite = "Strict"
    )

    private fun browserCookie(name: String, value: String, maxAge: Int? = null, sameSite: String = "Lax"): Cookie {
        return Cookie(
            name = name,
            value = value,
            httpOnly = true,
            secure = sessionConfig.cookieSecure,
            domain = if (name.startsWith("__Host-")) null else sessionConfig.cookieDomain,
            path = "/",
            maxAge = maxAge,
            extensions = mapOf("SameSite" to sameSite)
        )
    }

    private fun browserCookieName(name: String): String {
        return if (sessionConfig.cookieSecure) "__Host-$name" else name
    }

    private fun activeUserId(call: ApplicationCall): String? {
        return (call.request.cookies["__Host-$ACTIVE_USER_COOKIE_NAME"]
            ?: call.request.cookies[ACTIVE_USER_COOKIE_NAME])
            ?.let(::parseUserId)
    }

    private fun refreshCookieMaxAgeSeconds(): Int {
        return (sessionConfig.refreshTokenExpDays * 24 * 60 * 60)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun accessCookieMaxAgeSeconds(): Int {
        return (jwtConfig.accessTokenExpMinutes * 60)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun requestRefreshTokens(call: ApplicationCall): Map<String, String> {
        return call.request.cookies.rawCookies
            .filterKeys { it.startsWith(REFRESH_COOKIE_PREFIX) }
            .mapNotNull { (name, value) ->
                parseUserId(name.removePrefix(REFRESH_COOKIE_PREFIX))?.let { it to value }
            }
            .toMap()
    }

    private fun apiTokenResponse(accessToken: String, refreshToken: String) = ApiTokenResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = jwtConfig.accessTokenExpMinutes * 60
    )

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun ApplicationCall.clientIpAddress(): String {
        return request.headers["X-Real-IP"]
            ?: request.local.remoteHost
    }

    private fun ApplicationCall.emailLocale(): EmailLocale =
        EmailLocale.fromHeader(request.headers[HttpHeaders.AcceptLanguage])

    private companion object {
        private const val REFRESH_COOKIE_PREFIX = "refresh_token_"
        private const val REFRESH_COOKIE_PATH = "/api/auth"
        private const val ACCESS_COOKIE_NAME = "access_token"
        private const val ACTIVE_USER_COOKIE_NAME = "active_user"
        private const val CSRF_COOKIE_NAME = "csrf_token"
    }
}
