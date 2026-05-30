package profile.auth

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import profile.infrastructure.config.SessionConfig
import profile.infrastructure.db.Session
import profile.infrastructure.db.SessionRepository
import profile.infrastructure.db.User
import profile.infrastructure.db.UserRepository
import profile.infrastructure.db.VerificationToken
import profile.infrastructure.db.VerificationTokenRepository
import profile.infrastructure.events.EventPublisher
import profile.infrastructure.events.PasswordResetEmailPayload
import profile.infrastructure.events.VerificationEmailPayload
import profile.infrastructure.jwt.JwtIssuer
import profile.infrastructure.redis.PendingRegistration
import profile.infrastructure.redis.PendingRegistrationStore
import profile.infrastructure.security.PasswordHasher
import profile.infrastructure.security.TokenHasher
import profile.search.SearchService
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val verificationTokenRepository: VerificationTokenRepository,
    private val pendingRegistrationStore: PendingRegistrationStore,
    private val searchService: SearchService,
    private val jwtIssuer: JwtIssuer,
    private val eventPublisher: EventPublisher,
    private val sessionConfig: SessionConfig
) {
    private val random = SecureRandom()

    fun register(
        email: String,
        username: String,
        password: String,
        firstName: String? = null,
        lastName: String? = null
    ): RegistrationStartedResponse {
        val normalizedEmail = normalizeEmail(email)
        val normalizedUsername = normalizeUsername(username)
        ensureUserCanBeCreated(normalizedEmail, normalizedUsername)

        val code = generateVerificationCode()
        val pending = PendingRegistration(
            email = normalizedEmail,
            username = normalizedUsername,
            passwordHash = PasswordHasher.hash(password),
            firstName = firstName?.trim()?.takeIf { it.isNotBlank() },
            lastName = lastName?.trim()?.takeIf { it.isNotBlank() },
            codeHash = TokenHasher.hash(code)
        )

        pendingRegistrationStore.create(pending)
        publishVerificationEmail(normalizedEmail, code)

        return RegistrationStartedResponse(
            email = normalizedEmail,
            expiresInSeconds = pendingRegistrationStore.ttlSeconds,
            message = "Verification code sent"
        )
    }

    fun confirmRegistration(
        email: String,
        code: String,
        deviceId: String?,
        userAgent: String?,
        ipAddress: String?
    ): LoginResult {
        val normalizedEmail = normalizeEmail(email)
        val codeHash = TokenHasher.hash(code.trim())
        val emailForCode = pendingRegistrationStore.findEmailByCodeHash(codeHash)
            ?: throw IllegalArgumentException("Invalid or expired registration code")

        if (emailForCode != normalizedEmail) throw IllegalArgumentException("Invalid registration code")

        val pending = pendingRegistrationStore.findByEmail(normalizedEmail)
            ?: throw IllegalArgumentException("Pending registration not found")

        ensureUserCanBeCreated(pending.email, pending.username)

        val user = User(
            id = UUID.randomUUID().toString(),
            email = pending.email,
            username = pending.username,
            passwordHash = pending.passwordHash,
            emailVerified = true,
            firstName = pending.firstName,
            lastName = pending.lastName
        )

        userRepository.create(user)
        pendingRegistrationStore.delete(normalizedEmail)
        searchService.indexUser(user)
        eventPublisher.publish("user.created", user.id)

        return createSession(user, deviceId, userAgent, ipAddress)
    }

    fun resendRegistrationCode(email: String): RegistrationStartedResponse {
        val normalizedEmail = normalizeEmail(email)
        val pending = pendingRegistrationStore.findByEmail(normalizedEmail)
            ?: throw IllegalArgumentException("Pending registration not found")

        val code = generateVerificationCode()
        pendingRegistrationStore.refreshCode(normalizedEmail, TokenHasher.hash(code))
        publishVerificationEmail(pending.email, code)

        return RegistrationStartedResponse(
            email = pending.email,
            expiresInSeconds = pendingRegistrationStore.ttlSeconds,
            message = "Verification code resent"
        )
    }

    fun requestEmailVerification(userId: String) {
        val user = userRepository.findById(userId) ?: return
        if (user.emailVerified) return

        val code = generateVerificationCode()
        val token = VerificationToken(
            id = UUID.randomUUID().toString(),
            userId = userId,
            tokenHash = TokenHasher.hash(code),
            purpose = "EMAIL_VERIFICATION",
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        verificationTokenRepository.create(token)
        publishVerificationEmail(user.email, code)
    }

    fun verifyEmail(userId: String, code: String) {
        val hash = TokenHasher.hash(code.trim())
        val token = verificationTokenRepository.findByHash(hash)
            ?: throw IllegalArgumentException("Invalid or expired code")

        if (token.userId != userId) throw IllegalArgumentException("Invalid code")
        if (token.purpose != "EMAIL_VERIFICATION") throw IllegalArgumentException("Invalid code")
        if (token.expiresAt.isBefore(Instant.now())) throw IllegalArgumentException("Code expired")

        userRepository.updateEmailVerified(userId, true)
        verificationTokenRepository.markAsUsed(token.id)
    }

    fun forgotPassword(identifier: String) {
        val user = findUserByIdentifier(identifier) ?: return

        val resetCode = generateVerificationCode()
        val token = VerificationToken(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            tokenHash = TokenHasher.hash(resetCode),
            purpose = "PASSWORD_RESET",
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        verificationTokenRepository.create(token)

        eventPublisher.publish(
            "email.password_reset",
            Json.encodeToString(PasswordResetEmailPayload(user.email, resetCode))
        )
    }

    fun resetPassword(identifier: String, resetCode: String, newPassword: String) {
        val hash = TokenHasher.hash(resetCode.trim())
        val token = verificationTokenRepository.findByHash(hash)
            ?: throw IllegalArgumentException("Invalid or expired code")

        if (token.purpose != "PASSWORD_RESET") throw IllegalArgumentException("Invalid code")
        if (token.expiresAt.isBefore(Instant.now())) throw IllegalArgumentException("Code expired")

        val user = findUserByIdentifier(identifier) ?: throw IllegalArgumentException("Invalid account")
        if (user.id != token.userId) throw IllegalArgumentException("Invalid code")

        val newPasswordHash = PasswordHasher.hash(newPassword)
        userRepository.updatePassword(token.userId, newPasswordHash)
        verificationTokenRepository.markAsUsed(token.id)
        sessionRepository.revokeAllForUser(token.userId)
    }

    fun login(
        identifier: String,
        password: String,
        deviceId: String?,
        userAgent: String?,
        ipAddress: String?
    ): LoginResult {
        val user = findUserByIdentifier(identifier) ?: throw IllegalArgumentException("Invalid credentials")
        if (!PasswordHasher.verify(user.passwordHash, password)) throw IllegalArgumentException("Invalid credentials")

        return createSession(user, deviceId, userAgent, ipAddress)
    }

    private fun createSession(user: User, deviceId: String?, userAgent: String?, ipAddress: String?): LoginResult {
        val refreshToken = generateRefreshToken()
        val refreshTokenHash = TokenHasher.hash(refreshToken)

        val session = Session(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            refreshTokenHash = refreshTokenHash,
            deviceId = deviceId?.takeIf { it.isNotBlank() },
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
        val newRefreshToken = generateRefreshToken()
        val newRefreshTokenHash = TokenHasher.hash(newRefreshToken)
        val newExpiresAt = Instant.now().plus(sessionConfig.refreshTokenExpDays, ChronoUnit.DAYS)

        if (!sessionRepository.rotateToken(session.id, refreshTokenHash, newRefreshTokenHash, newExpiresAt)) {
            throw IllegalArgumentException("Invalid refresh token")
        }

        val accessToken = jwtIssuer.createToken(user.id, session.id, user.role)

        return RefreshResult(accessToken, newRefreshToken, session.id, user.id)
    }

    fun logout(refreshToken: String) {
        val refreshTokenHash = TokenHasher.hash(refreshToken)
        val session = sessionRepository.findByTokenHash(refreshTokenHash) ?: return
        sessionRepository.revoke(session.id)
    }

    fun logoutAll(userId: String) {
        sessionRepository.revokeAllForUser(userId)
    }

    private fun ensureUserCanBeCreated(email: String, username: String) {
        if (userRepository.findByEmail(email) != null) throw IllegalArgumentException("Email already in use")
        if (userRepository.findByUsername(username) != null) throw IllegalArgumentException("Username already in use")
    }

    private fun findUserByIdentifier(identifier: String): User? {
        val normalized = identifier.trim()
        if (normalized.isBlank()) return null
        return if (normalized.contains("@")) {
            userRepository.findByEmail(normalizeEmail(normalized))
        } else {
            userRepository.findByUsername(normalized)
        }
    }

    private fun publishVerificationEmail(email: String, code: String) {
        eventPublisher.publish("email.verify", Json.encodeToString(VerificationEmailPayload(email, code)))
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private fun normalizeUsername(username: String): String = username.trim()

    private fun generateVerificationCode(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    private fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
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
    val refreshToken: String,
    val sessionId: String,
    val userId: String
)
