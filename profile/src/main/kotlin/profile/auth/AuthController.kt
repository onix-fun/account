package profile.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import profile.users.toProfileDto

class AuthController(
    private val authService: AuthService,
    private val sessionConfig: profile.infrastructure.config.SessionConfig
) {

    suspend fun register(call: ApplicationCall) {
        val request = call.receive<RegisterRequest>()
        val response = authService.register(
            request.email,
            request.username,
            request.password,
            request.firstName,
            request.lastName
        )
        call.respond(HttpStatusCode.Accepted, response)
    }

    suspend fun confirmRegistration(call: ApplicationCall) {
        val request = call.receive<ConfirmRegistrationRequest>()
        val userAgent = call.request.headers["User-Agent"]
        val ipAddress = call.clientIpAddress()
        val result =
            authService.confirmRegistration(request.email, request.code, request.deviceId, userAgent, ipAddress)
        call.response.cookies.append(refreshCookie(result.user.id, result.refreshToken))
        call.respond(
            HttpStatusCode.Created,
            AuthResponse(result.accessToken, result.user.id, result.user.toProfileDto())
        )
    }

    suspend fun resendRegistrationCode(call: ApplicationCall) {
        val request = call.receive<ResendRegistrationCodeRequest>()
        val response = authService.resendRegistrationCode(request.email)
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

        call.response.cookies.append(refreshCookie(result.user.id, result.refreshToken))

        call.respond(HttpStatusCode.OK, AuthResponse(result.accessToken, result.user.id, result.user.toProfileDto()))
    }

    suspend fun verifyEmail(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val request = call.receive<VerifyEmailRequest>()
        authService.verifyEmail(userId, request.code)
        call.respond(HttpStatusCode.OK, mapOf("message" to "Email verified successfully"))
    }

    suspend fun resendVerification(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        authService.requestEmailVerification(userId)
        call.respond(HttpStatusCode.OK, mapOf("message" to "Verification code resent"))
    }

    suspend fun forgotPassword(call: ApplicationCall) {
        val request = call.receive<ForgotPasswordRequest>()
        authService.forgotPassword(request.identifier ?: request.email.orEmpty())
        call.respond(HttpStatusCode.OK, mapOf("message" to "If the account exists, a reset code has been sent"))
    }

    suspend fun resetPassword(call: ApplicationCall) {
        val request = call.receive<ResetPasswordRequest>()
        authService.resetPassword(
            request.identifier ?: request.email.orEmpty(),
            request.code ?: request.token.orEmpty(),
            request.newPassword
        )
        call.respond(HttpStatusCode.OK, mapOf("message" to "Password has been reset successfully"))
    }

    suspend fun refresh(call: ApplicationCall) {
        val requestedUserId = call.request.header("X-User-Id")
        val cookieName = getCookieName(requestedUserId)
        val refreshToken = call.request.cookies[cookieName] ?: call.request.cookies["refresh_token"]

        if (refreshToken == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing refresh token cookie"))
            return
        }

        val result = authService.refresh(refreshToken)

        call.response.cookies.append(refreshCookie(result.userId, result.refreshToken))

        call.respond(HttpStatusCode.OK, mapOf("accessToken" to result.accessToken))
    }

    suspend fun logout(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.header("X-User-Id")
        val cookieName = getCookieName(userId)
        val refreshToken = call.request.cookies[cookieName] ?: call.request.cookies["refresh_token"]
        if (refreshToken != null) {
            authService.logout(refreshToken)
        }

        call.response.cookies.append(clearRefreshCookie(userId))

        call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
    }

    suspend fun logoutAll(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()?.payload?.subject
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
            return
        }

        authService.logoutAll(userId)

        call.response.cookies.append(clearRefreshCookie(userId))

        call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out from all devices"))
    }

    private fun getCookieName(userId: String?): String {
        return if (userId != null) "${REFRESH_COOKIE_NAME}_$userId" else REFRESH_COOKIE_NAME
    }

    private fun refreshCookie(userId: String, value: String): Cookie {
        return Cookie(
            name = getCookieName(userId),
            value = value,
            httpOnly = true,
            secure = sessionConfig.cookieSecure,
            path = REFRESH_COOKIE_PATH,
            maxAge = refreshCookieMaxAgeSeconds(),
            extensions = mapOf("SameSite" to "Lax")
        )
    }

    private fun clearRefreshCookie(userId: String?): Cookie {
        return Cookie(
            name = getCookieName(userId),
            value = "",
            httpOnly = true,
            secure = sessionConfig.cookieSecure,
            path = REFRESH_COOKIE_PATH,
            maxAge = 0,
            extensions = mapOf("SameSite" to "Lax")
        )
    }

    private fun refreshCookieMaxAgeSeconds(): Int {
        return (sessionConfig.refreshTokenExpDays * 24 * 60 * 60)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun ApplicationCall.clientIpAddress(): String {
        return request.headers["X-Forwarded-For"]
            ?.substringBefore(",")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.headers["X-Real-IP"]
            ?: request.local.remoteHost
    }

    private companion object {
        private const val REFRESH_COOKIE_NAME = "refresh_token"
        private const val REFRESH_COOKIE_PATH = "/api/auth"
    }
}
