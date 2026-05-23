package profile.auth

import profile.infrastructure.db.UserRepository
import profile.infrastructure.db.SessionRepository
import profile.infrastructure.db.Session
import profile.infrastructure.db.VerificationTokenRepository
import profile.infrastructure.db.VerificationToken
import profile.infrastructure.db.User
import profile.infrastructure.security.TokenHasher
import profile.users.toPublicDto
import profile.infrastructure.jwt.JwtIssuer
import profile.infrastructure.security.PasswordHasher
import profile.infrastructure.events.EventPublisher
import profile.infrastructure.config.SessionConfig
import profile.search.SearchService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class AuthService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val verificationTokenRepository: VerificationTokenRepository,
    private val SearchService: SearchService,
    private val jwtIssuer: JwtIssuer,
    private val eventPublisher: EventPublisher,
    private val sessionConfig: SessionConfig
) {
    fun register(email: String, username: String, password: String, firstName: String? = null, lastName: String? = null): User {
        val existing = userRepository.findByEmail(email)
        if (existing != null) throw IllegalArgumentException("User already exists")

        val user = User(
            id = UUID.randomUUID().toString(),
            email = email,
            username = username,
            passwordHash = PasswordHasher.hash(password),
            firstName = firstName,
            lastName = lastName
        )
        userRepository.create(user)
        SearchService.indexUser(user)
        eventPublisher.publish("user.created", user.id)

        requestEmailVerification(user.id)

        return user
    }

    fun requestEmailVerification(userId: String) {
        val user = userRepository.findById(userId) ?: return
        if (user.emailVerified) return

        // 6-digit code for email verification
        val code = (100000..999999).random().toString()
        val token = VerificationToken(
            id = UUID.randomUUID().toString(),
            userId = userId,
            tokenHash = TokenHasher.hash(code),
            purpose = "EMAIL_VERIFICATION",
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        verificationTokenRepository.create(token)

        // Async email sending
        eventPublisher.publish("email.verify", """{"email":"${user.email}","code":"$code"}""")
    }

    fun verifyEmail(userId: String, code: String) {
        val hash = TokenHasher.hash(code)
        val token = verificationTokenRepository.findByHash(hash) 
            ?: throw IllegalArgumentException("Invalid or expired code")

        if (token.userId != userId) throw IllegalArgumentException("Invalid code")
        if (token.purpose != "EMAIL_VERIFICATION") throw IllegalArgumentException("Invalid code")
        if (token.expiresAt.isBefore(Instant.now())) throw IllegalArgumentException("Code expired")

        userRepository.updateEmailVerified(userId, true)
        verificationTokenRepository.markAsUsed(token.id)
    }

    fun forgotPassword(email: String) {
        val user = userRepository.findByEmail(email) ?: return // Don't leak user existence

        val resetToken = UUID.randomUUID().toString()
        val token = VerificationToken(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            tokenHash = TokenHasher.hash(resetToken),
            purpose = "PASSWORD_RESET",
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        verificationTokenRepository.create(token)

        // Async email sending
        eventPublisher.publish("email.password_reset", """{"email":"${user.email}","token":"$resetToken"}""")
    }

    fun resetPassword(resetToken: String, newPassword: String) {
        val hash = TokenHasher.hash(resetToken)
        val token = verificationTokenRepository.findByHash(hash) 
            ?: throw IllegalArgumentException("Invalid or expired token")

        if (token.purpose != "PASSWORD_RESET") throw IllegalArgumentException("Invalid token")
        if (token.expiresAt.isBefore(Instant.now())) throw IllegalArgumentException("Token expired")

        val newPasswordHash = PasswordHasher.hash(newPassword)
        userRepository.updatePassword(token.userId, newPasswordHash)
        verificationTokenRepository.markAsUsed(token.id)

        // Optional: revoke all sessions after password reset
        sessionRepository.revokeAllForUser(token.userId)
    }

    fun login(email: String, password: String, deviceId: String?, userAgent: String?, ipAddress: String?): LoginResult {
        val user = userRepository.findByEmail(email) ?: throw IllegalArgumentException("Invalid credentials")
        if (!PasswordHasher.verify(user.passwordHash, password)) throw IllegalArgumentException("Invalid credentials")

        val refreshToken = UUID.randomUUID().toString()
        val refreshTokenHash = TokenHasher.hash(refreshToken)

        val session = Session(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            refreshTokenHash = refreshTokenHash,
            deviceId = deviceId,
            userAgent = userAgent,
            ipAddress = ipAddress,
            expiresAt = Instant.now().plus(sessionConfig.refreshTokenExpDays, ChronoUnit.DAYS)
        )
        sessionRepository.create(session)

        val accessToken = jwtIssuer.createToken(user.id, session.id, user.role)

        return LoginResult(accessToken, refreshToken, session.id, user)
    }

    fun refresh(refreshToken: String): RefreshResult {
        val refreshTokenHash = TokenHasher.hash(refreshToken)
        val session = sessionRepository.findByTokenHash(refreshTokenHash) 
            ?: throw IllegalArgumentException("Invalid refresh token")

        if (session.revokedAt != null) throw IllegalArgumentException("Session revoked")
        if (session.expiresAt.isBefore(Instant.now())) throw IllegalArgumentException("Session expired")

        val user = userRepository.findById(session.userId) ?: throw IllegalArgumentException("User not found")

        val newExpiresAt = Instant.now().plus(sessionConfig.refreshTokenExpDays, ChronoUnit.DAYS)
        sessionRepository.updateExpiration(session.id, newExpiresAt)

        val accessToken = jwtIssuer.createToken(user.id, session.id, user.role)

        return RefreshResult(accessToken, session.id)
    }

    fun logout(refreshToken: String) {
        val refreshTokenHash = TokenHasher.hash(refreshToken)
        val session = sessionRepository.findByTokenHash(refreshTokenHash) ?: return
        sessionRepository.revoke(session.id)
    }

    fun logoutAll(userId: String) {
        sessionRepository.revokeAllForUser(userId)
    }
}

data class LoginResult(
    val accessToken: String,
    val refreshToken: String,
    val sessionId: String,
    val user: User
)

data class RefreshResult(
    val accessToken: String,
    val sessionId: String
)
