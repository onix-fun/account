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
import profile.infrastructure.db.PendingEmailChange
import profile.infrastructure.db.PendingEmailChangeRepository
import profile.infrastructure.db.AuditRepository
import profile.infrastructure.config.SecurityConfig
import profile.infrastructure.events.EventPublisher
import profile.infrastructure.events.PasswordResetEmailPayload
import profile.infrastructure.events.VerificationEmailPayload
import profile.infrastructure.events.SecurityNotificationPayload
import profile.infrastructure.events.EmailLocale
import profile.infrastructure.events.SecurityNoticeType
import profile.infrastructure.jwt.JwtIssuer
import profile.infrastructure.redis.PendingRegistration
import profile.infrastructure.redis.PendingRegistrationStore
import profile.infrastructure.redis.RedisManager
import profile.infrastructure.security.PasswordHasher
import profile.infrastructure.security.TokenHasher
import profile.infrastructure.security.EmailNormalizer
import profile.search.SearchService
import profile.shared.ApiErrorCode
import profile.shared.apiError
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

class AuthService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val verificationTokenRepository: VerificationTokenRepository,
    private val pendingRegistrationStore: PendingRegistrationStore,
    private val searchService: SearchService,
    private val jwtIssuer: JwtIssuer,
    private val eventPublisher: EventPublisher,
    private val sessionConfig: SessionConfig,
    private val securityConfig: SecurityConfig,
    private val pendingEmailChangeRepository: PendingEmailChangeRepository,
    private val redisManager: RedisManager,
    private val auditRepository: AuditRepository
) {
    private val random = SecureRandom()

    fun register(
        email: String,
        username: String,
        password: String,
        firstName: String? = null,
        lastName: String? = null,
        locale: EmailLocale = EmailLocale.EN
    ): RegistrationStartedResponse {
        val normalizedEmail = normalizeEmail(email)
        val normalizedUsername = normalizeUsername(username)
        if (normalizedUsername.length < 3) apiError(ApiErrorCode.VALIDATION_USERNAME_TOO_SHORT, "username")
        ensureUserCanBeCreated(normalizedEmail, normalizedUsername)

        val code = generateVerificationCode()
        val pending = PendingRegistration(
            email = normalizedEmail,
            username = normalizedUsername,
            passwordHash = PasswordHasher.hash(password),
            firstName = firstName?.trim()?.takeIf { it.isNotBlank() },
            lastName = lastName?.trim()?.takeIf { it.isNotBlank() },
            codeHash = TokenHasher.challenge(securityConfig.otpHmacSecret, "REGISTRATION", normalizedEmail, code)
        )

        pendingRegistrationStore.create(pending)
        publishVerificationEmail(normalizedEmail, code, locale)

        return RegistrationStartedResponse(
            expiresInSeconds = 900
        )
    }

    fun confirmRegistration(
        identifier: String,
        code: String,
        deviceId: String?,
        userAgent: String?,
        ipAddress: String?
    ): LoginResult {
        val pendingForIdentifier = pendingRegistrationStore.findByIdentifier(identifier)
            ?: apiError(ApiErrorCode.AUTH_PENDING_REGISTRATION_NOT_FOUND, "identifier")
        val normalizedEmail = pendingForIdentifier.email
        val codeHash = TokenHasher.challenge(securityConfig.otpHmacSecret, "REGISTRATION", normalizedEmail, code.trim())
        pendingRegistrationStore.verifyCode(normalizedEmail, codeHash)

        val pending = pendingRegistrationStore.findByEmail(normalizedEmail)
            ?: apiError(ApiErrorCode.AUTH_PENDING_REGISTRATION_NOT_FOUND, "identifier")

        ensureUserCanBeCreated(pending.email, pending.username)

        val user = User(
            email = pending.email,
            username = pending.username,
            passwordHash = pending.passwordHash,
            emailVerified = true,
            firstName = pending.firstName,
            lastName = pending.lastName
        )

        val createdUser = userRepository.create(user)
        pendingRegistrationStore.delete(normalizedEmail)
        searchService.indexUser(createdUser)
        eventPublisher.publish("user.created", createdUser.id)

        return createSession(createdUser, deviceId, userAgent, ipAddress)
    }

    fun resendRegistrationCode(identifier: String, locale: EmailLocale = EmailLocale.EN): RegistrationStartedResponse {
        enforcePublicRate("registration-resend", identifier)
        val pending = pendingRegistrationStore.findByIdentifier(identifier)
            ?: apiError(ApiErrorCode.AUTH_PENDING_REGISTRATION_NOT_FOUND, "identifier")
        val normalizedEmail = pending.email

        val code = generateVerificationCode()
        pendingRegistrationStore.refreshCode(normalizedEmail, TokenHasher.challenge(securityConfig.otpHmacSecret, "REGISTRATION", normalizedEmail, code))
        publishVerificationEmail(pending.email, code, locale)

        return RegistrationStartedResponse(
            expiresInSeconds = 900
        )
    }

    fun requestEmailVerification(userId: String, locale: EmailLocale = EmailLocale.EN) {
        val user = userRepository.findById(userId) ?: return
        if (user.emailVerified) return

        enforceCodeCooldown(userId, "EMAIL_VERIFICATION")
        val code = generateVerificationCode()
        val token = VerificationToken(
            userId = userId,
            tokenHash = challengeHash("EMAIL_VERIFICATION", userId, code),
            purpose = "EMAIL_VERIFICATION",
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
        )
        verificationTokenRepository.create(token)
        publishVerificationEmail(user.email, code, locale)
    }

    fun verifyEmail(userId: String, code: String) {
        verificationTokenRepository.verify(userId, "EMAIL_VERIFICATION", challengeHash("EMAIL_VERIFICATION", userId, code.trim()))

        userRepository.updateEmailVerified(userId, true)
    }

    fun forgotPassword(identifier: String, locale: EmailLocale = EmailLocale.EN) {
        enforcePublicRate("password-reset", identifier)
        val user = findUserByIdentifier(identifier) ?: return

        enforceCodeCooldown(user.id, "PASSWORD_RESET")
        val resetCode = generateVerificationCode()
        val token = VerificationToken(
            userId = user.id,
            tokenHash = challengeHash("PASSWORD_RESET", user.id, resetCode),
            purpose = "PASSWORD_RESET",
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
        )
        verificationTokenRepository.create(token)

        eventPublisher.publish(
            "email.password_reset",
            Json.encodeToString(PasswordResetEmailPayload(user.email, resetCode, locale))
        )
    }

    fun resetPassword(identifier: String, resetCode: String, newPassword: String, locale: EmailLocale = EmailLocale.EN) {
        val user = findUserByIdentifier(identifier) ?: apiError(ApiErrorCode.AUTH_ACCOUNT_NOT_FOUND, "identifier")
        verificationTokenRepository.verify(user.id, "PASSWORD_RESET", challengeHash("PASSWORD_RESET", user.id, resetCode.trim()))

        val newPasswordHash = PasswordHasher.hash(newPassword)
        userRepository.updatePassword(user.id, newPasswordHash)
        verificationTokenRepository.invalidateAll(user.id, "PASSWORD_RESET")
        revokeCachedSessions(user.id)
        sessionRepository.revokeAllForUser(user.id)
        auditRepository.record(user.id, "PASSWORD_RESET", "SUCCESS")
        val userEmail = userRepository.findById(user.id)?.email
        if (userEmail != null) {
            publishSecurityNotice(userEmail, SecurityNoticeType.PASSWORD_RESET, locale)
        }
    }

    fun changePassword(userId: String, currentPassword: String, newPassword: String, locale: EmailLocale = EmailLocale.EN) {
        val user = userRepository.findById(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        if (!PasswordHasher.verify(user.passwordHash, currentPassword)) {
            apiError(ApiErrorCode.AUTH_INVALID_PASSWORD, "currentPassword")
        }

        userRepository.updatePassword(userId, PasswordHasher.hash(newPassword))
        revokeCachedSessions(userId)
        sessionRepository.revokeAllForUser(userId)
        auditRepository.record(userId, "PASSWORD_CHANGED", "SUCCESS")
        publishSecurityNotice(user.email, SecurityNoticeType.PASSWORD_CHANGED, locale)
    }

    fun deleteAccount(userId: String, password: String) {
        val user = userRepository.findById(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        if (!PasswordHasher.verify(user.passwordHash, password)) {
            apiError(ApiErrorCode.AUTH_INVALID_PASSWORD, "password")
        }

        userRepository.delete(userId)
    }

    fun login(
        identifier: String,
        password: String,
        deviceId: String?,
        userAgent: String?,
        ipAddress: String?
    ): LoginResult {
        val user = findUserByIdentifier(identifier) ?: apiError(ApiErrorCode.AUTH_INVALID_CREDENTIALS, "password")
        if (user.status != "ACTIVE") apiError(ApiErrorCode.AUTH_ACCOUNT_BLOCKED, "identifier")
        if (!user.emailVerified) apiError(ApiErrorCode.AUTH_EMAIL_NOT_VERIFIED, "identifier")

        if (redisManager.getAccountFailedAttempts(user.id) >= 5) {
            apiError(ApiErrorCode.AUTH_ACCOUNT_BLOCKED, "identifier")
        }

        if (!PasswordHasher.verify(user.passwordHash, password)) {
            redisManager.incrementAccountFailedAttempts(user.id)
            apiError(ApiErrorCode.AUTH_INVALID_CREDENTIALS, "password")
        }

        redisManager.clearAccountFailedAttempts(user.id)
        return createSession(user, deviceId, userAgent, ipAddress)
    }

    private fun createSession(user: User, deviceId: String?, userAgent: String?, ipAddress: String?): LoginResult {
        val refreshToken = generateRefreshToken()
        val refreshTokenHash = TokenHasher.hash(refreshToken)

        val session = Session(
            userId = user.id,
            refreshTokenHash = refreshTokenHash,
            deviceId = deviceId?.takeIf { it.isNotBlank() },
            userAgent = userAgent,
            ipAddress = ipAddress,
            expiresAt = Instant.now().plus(sessionConfig.refreshTokenExpDays, ChronoUnit.DAYS)
        )
        val sessionId = sessionRepository.create(session)
        redisManager.activateSession(sessionId, user.id, sessionConfig.refreshTokenExpDays * 86400)

        val accessToken = jwtIssuer.createToken(user.id, sessionId)

        return LoginResult(accessToken, refreshToken, sessionId, user)
    }

    fun refresh(refreshToken: String, allowPreviousToken: Boolean = false): RefreshResult {
        val refreshTokenHash = TokenHasher.hash(refreshToken)
        val session = if (allowPreviousToken) {
            sessionRepository.findByTokenHashWithGrace(refreshTokenHash)
        } else {
            sessionRepository.findByTokenHash(refreshTokenHash)
        }
            ?: apiError(ApiErrorCode.AUTH_INVALID_REFRESH_TOKEN)

        validateSession(session)

        val user = userRepository.findById(session.userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        val newRefreshToken = generateRefreshToken()
        val newRefreshTokenHash = TokenHasher.hash(newRefreshToken)
        val newExpiresAt = Instant.now().plus(sessionConfig.refreshTokenExpDays, ChronoUnit.DAYS)

        if (!sessionRepository.rotateToken(session.id, refreshTokenHash, newRefreshTokenHash, newExpiresAt, allowPreviousToken)) {
            apiError(ApiErrorCode.AUTH_INVALID_REFRESH_TOKEN)
        }

        val accessToken = jwtIssuer.createToken(user.id, session.id)

        return RefreshResult(accessToken, newRefreshToken, session.id, user)
    }

    fun accountForRefreshToken(refreshToken: String, allowPreviousToken: Boolean = false): User? {
        val refreshTokenHash = TokenHasher.hash(refreshToken)
        val session = if (allowPreviousToken) {
            sessionRepository.findByTokenHashWithGrace(refreshTokenHash)
        } else {
            sessionRepository.findByTokenHash(refreshTokenHash)
        } ?: return null
        if (session.revokedAt != null || !session.expiresAt.isAfter(Instant.now())) return null
        return userRepository.findById(session.userId)
    }

    fun logout(refreshToken: String) {
        val refreshTokenHash = TokenHasher.hash(refreshToken)
        val session = sessionRepository.findByTokenHash(refreshTokenHash) ?: return
        sessionRepository.revoke(session.id)
        redisManager.revokeSession(session.id)
        auditRepository.record(session.userId, "SESSION_REVOKED", "SUCCESS")
    }

    fun logoutAll(userId: String) {
        revokeCachedSessions(userId)
        sessionRepository.revokeAllForUser(userId)
        auditRepository.record(userId, "SESSIONS_REVOKED_ALL", "SUCCESS")
    }

    fun isUsernameAvailable(username: String): Boolean {
        val normalized = normalizeUsername(username)
        if (normalized.length < 3) return false
        return userRepository.findByUsername(normalized) == null &&
            !pendingRegistrationStore.isUsernameReserved(normalized)
    }

    private fun validateSession(session: Session) {
        if (session.revokedAt != null) apiError(ApiErrorCode.AUTH_SESSION_REVOKED)
        if (!session.expiresAt.isAfter(Instant.now())) apiError(ApiErrorCode.AUTH_SESSION_EXPIRED)
    }

    private fun ensureUserCanBeCreated(email: String, username: String) {
        if (userRepository.findByEmail(email) != null) apiError(ApiErrorCode.AUTH_EMAIL_IN_USE, "email")
        if (userRepository.findByUsername(username) != null) apiError(ApiErrorCode.AUTH_USERNAME_IN_USE, "username")
    }

    fun lookupAccount(identifier: String): AccountLookupResponse {
        val normalized = identifier.trim()
        if (normalized.isBlank()) apiError(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "identifier")
        val lookupIdentifier = if (normalized.contains("@")) {
            EmailNormalizer.normalize(normalized, "identifier")
        } else {
            normalized
        }
        val pending = pendingRegistrationStore.findByIdentifier(lookupIdentifier)
        if (pending != null) {
            return AccountLookupResponse("PENDING_REGISTRATION", lookupIdentifier)
        }
        val user = findUserByIdentifier(lookupIdentifier)
            ?: return AccountLookupResponse("NOT_FOUND", lookupIdentifier)
        val state = when {
            user.status != "ACTIVE" -> "BLOCKED"
            !user.emailVerified -> "EMAIL_UNVERIFIED"
            normalized.contains("@") -> "EMAIL_LOGIN"
            else -> "ACTIVE"
        }
        return AccountLookupResponse(state, lookupIdentifier, user.avatarUrl)
    }

    fun requestPublicEmailVerification(identifier: String, locale: EmailLocale = EmailLocale.EN) {
        enforcePublicRate("public-verification", identifier)
        val user = findUserByIdentifier(identifier) ?: apiError(ApiErrorCode.AUTH_ACCOUNT_NOT_FOUND, "identifier")
        if (user.status != "ACTIVE") apiError(ApiErrorCode.AUTH_ACCOUNT_BLOCKED, "identifier")
        if (!user.emailVerified) requestEmailVerification(user.id, locale)
    }

    fun confirmPublicEmailVerification(identifier: String, code: String) {
        val user = findUserByIdentifier(identifier) ?: apiError(ApiErrorCode.AUTH_ACCOUNT_NOT_FOUND, "identifier")
        verifyEmail(user.id, code)
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

    private fun publishVerificationEmail(email: String, code: String, locale: EmailLocale) {
        eventPublisher.publish("email.verify", Json.encodeToString(VerificationEmailPayload(email, code, locale)))
    }

    fun requestEmailChange(userId: String, currentPassword: String, newEmailRaw: String, locale: EmailLocale = EmailLocale.EN) {
        val user = userRepository.findById(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        if (!PasswordHasher.verify(user.passwordHash, currentPassword)) apiError(ApiErrorCode.AUTH_INVALID_PASSWORD, "currentPassword")
        val newEmail = EmailNormalizer.normalize(newEmailRaw, "newEmail")
        if (newEmail == user.email || userRepository.findByEmail(newEmail) != null) apiError(ApiErrorCode.AUTH_EMAIL_IN_USE, "newEmail")
        pendingEmailChangeRepository.upsert(PendingEmailChange(userId, newEmail, Instant.now().plus(15, ChronoUnit.MINUTES)))
        val code = generateVerificationCode()
        verificationTokenRepository.create(VerificationToken(userId = userId, tokenHash = challengeHash("EMAIL_CHANGE", userId, code), purpose = "EMAIL_CHANGE", expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)))
        eventPublisher.publish("email.email_change", Json.encodeToString(VerificationEmailPayload(newEmail, code, locale)))
    }

    fun confirmEmailChange(userId: String, sessionId: String, code: String, locale: EmailLocale = EmailLocale.EN) {
        val oldEmail = userRepository.findById(userId)?.email ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        val pending = pendingEmailChangeRepository.find(userId) ?: apiError(ApiErrorCode.AUTH_EMAIL_CHANGE_NOT_FOUND)
        verificationTokenRepository.verify(userId, "EMAIL_CHANGE", challengeHash("EMAIL_CHANGE", userId, code.trim()))
        if (userRepository.findByEmail(pending.newEmail) != null) apiError(ApiErrorCode.AUTH_EMAIL_IN_USE, "newEmail")
        userRepository.updateEmail(userId, pending.newEmail)
        pendingEmailChangeRepository.delete(userId)
        sessionRepository.findActiveByUserId(userId).filter { it.id != sessionId }.forEach { redisManager.revokeSession(it.id) }
        sessionRepository.revokeAllExcept(userId, sessionId)
        auditRepository.record(userId, "EMAIL_CHANGED", "SUCCESS")
        publishSecurityNotice(oldEmail, SecurityNoticeType.EMAIL_CHANGED, locale)
        publishSecurityNotice(pending.newEmail, SecurityNoticeType.EMAIL_ADDED, locale)
    }

    fun cancelEmailChange(userId: String) {
        pendingEmailChangeRepository.delete(userId)
        verificationTokenRepository.invalidateAll(userId, "EMAIL_CHANGE")
    }

    private fun enforceCodeCooldown(userId: String, purpose: String) {
        val latest = verificationTokenRepository.latest(userId, purpose)
        if (latest != null && latest.createdAt.isAfter(Instant.now().minusSeconds(60))) apiError(ApiErrorCode.AUTH_CODE_RESEND_TOO_SOON)
    }

    private fun challengeHash(purpose: String, subjectId: String, code: String) =
        TokenHasher.challenge(securityConfig.otpHmacSecret, purpose, subjectId, code)

    private fun enforcePublicRate(scope: String, identifier: String) {
        val key = TokenHasher.challenge(securityConfig.otpHmacSecret, "RATE", scope, identifier.trim().lowercase())
        if (!redisManager.checkRateLimit(scope, key, 20, 3600)) apiError(ApiErrorCode.INFRASTRUCTURE_RATE_LIMITED)
    }

    private fun revokeCachedSessions(userId: String) {
        sessionRepository.findActiveByUserId(userId).forEach { redisManager.revokeSession(it.id) }
    }

    private fun publishSecurityNotice(email: String, type: SecurityNoticeType, locale: EmailLocale) {
        eventPublisher.publish("email.security_notice", Json.encodeToString(SecurityNotificationPayload(email, type, locale)))
    }

    private fun normalizeEmail(email: String): String = EmailNormalizer.normalize(email)

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
    val user: User
)
