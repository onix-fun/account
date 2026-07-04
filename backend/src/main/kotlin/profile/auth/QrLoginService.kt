package profile.auth

import kotlinx.serialization.Serializable
import profile.infrastructure.config.SecurityConfig
import profile.infrastructure.db.QrLoginChallenge
import profile.infrastructure.db.QrLoginChallengeRepository
import profile.infrastructure.db.QrLoginChallengeRepository.ConsumeResult
import profile.infrastructure.db.UserRepository
import profile.infrastructure.security.TokenHasher
import profile.shared.ApiErrorCode
import profile.shared.apiError
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

class QrLoginService(
    private val challengeRepository: QrLoginChallengeRepository,
    private val userRepository: UserRepository,
    private val authService: AuthService,
    private val securityConfig: SecurityConfig
) {
    private val random = SecureRandom()

    fun createChallenge(userId: String): QrLoginChallengeCreatedResponse {
        val scanToken = generateScanToken()
        val manualCode = generateManualCode()
        val challenge = challengeRepository.create(
            userId = userId,
            scanTokenHash = hashScanToken(scanToken),
            manualCodeHash = hashManualCode(manualCode),
            expiresAt = Instant.now().plus(CHALLENGE_TTL_SECONDS, ChronoUnit.SECONDS)
        )
        return QrLoginChallengeCreatedResponse(
            id = challenge.id,
            scanToken = scanToken,
            manualCode = formatManualCode(manualCode),
            status = challenge.status,
            expiresAt = challenge.expiresAt.toString()
        )
    }

    fun getChallenge(userId: String, id: String): QrLoginChallengeStatusResponse {
        val challenge = challengeRepository.findForUser(id, userId)
            ?: apiError(ApiErrorCode.AUTH_QR_CHALLENGE_NOT_FOUND, "id")
        return challenge.toStatusResponse()
    }

    fun cancelChallenge(userId: String, id: String): QrLoginChallengeStatusResponse {
        val challenge = challengeRepository.cancel(id, userId)
            ?: apiError(ApiErrorCode.AUTH_QR_CHALLENGE_NOT_FOUND, "id")
        return challenge.toStatusResponse()
    }

    fun consume(request: QrLoginConsumeRequest, deviceId: String?, userAgent: String?, ipAddress: String?): LoginResult {
        val usingScanToken = !request.scanToken.isNullOrBlank()
        val errorField = if (usingScanToken) "scanToken" else "manualCode"
        val result = when {
            usingScanToken ->
                challengeRepository.consumeByScanTokenHash(hashScanToken(request.scanToken), deviceId, userAgent, ipAddress)
            !request.manualCode.isNullOrBlank() ->
                challengeRepository.consumeByManualCodeHash(hashManualCode(normalizeManualCode(request.manualCode)), deviceId, userAgent, ipAddress)
            else -> apiError(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "scanToken", "manualCode")
        }

        val challenge = when (result) {
            is ConsumeResult.Consumed -> result.challenge
            is ConsumeResult.NotConsumed -> {
                val found = result.challenge ?: apiError(ApiErrorCode.AUTH_QR_CODE_INVALID, errorField)
                when {
                    found.status == "CONSUMED" -> apiError(ApiErrorCode.AUTH_QR_CHALLENGE_CONSUMED)
                    found.status != "PENDING" -> apiError(ApiErrorCode.AUTH_QR_CHALLENGE_NOT_FOUND)
                    !found.expiresAt.isAfter(Instant.now()) -> apiError(ApiErrorCode.AUTH_QR_CHALLENGE_EXPIRED)
                    else -> apiError(ApiErrorCode.AUTH_QR_CODE_INVALID, errorField)
                }
            }
        }

        val user = userRepository.findById(challenge.userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        if (user.status != "ACTIVE") apiError(ApiErrorCode.AUTH_ACCOUNT_BLOCKED)
        if (!user.emailVerified) apiError(ApiErrorCode.AUTH_EMAIL_NOT_VERIFIED)
        return authService.createSession(user, deviceId, userAgent, ipAddress)
    }

    private fun QrLoginChallenge.toStatusResponse() = QrLoginChallengeStatusResponse(
        id = id,
        status = if (status == "PENDING" && !expiresAt.isAfter(Instant.now())) "EXPIRED" else status,
        expiresAt = expiresAt.toString(),
        consumedAt = consumedAt?.toString()
    )

    private fun hashScanToken(value: String) = TokenHasher.challenge(securityConfig.otpHmacSecret, "QR_LOGIN_SCAN", "global", value.trim())

    private fun hashManualCode(value: String) = TokenHasher.challenge(securityConfig.otpHmacSecret, "QR_LOGIN_MANUAL", "global", normalizeManualCode(value))

    private fun generateScanToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateManualCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..12).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun normalizeManualCode(value: String): String = value.filter(Char::isLetterOrDigit).uppercase()

    private fun formatManualCode(value: String): String = normalizeManualCode(value).chunked(4).joinToString("-")

    private companion object {
        private const val CHALLENGE_TTL_SECONDS = 120L
    }
}

@Serializable
data class QrLoginChallengeCreatedResponse(
    val id: String,
    val scanToken: String,
    val manualCode: String,
    val status: String,
    val expiresAt: String
)

@Serializable
data class QrLoginChallengeStatusResponse(
    val id: String,
    val status: String,
    val expiresAt: String,
    val consumedAt: String? = null
)

@Serializable
data class QrLoginConsumeRequest(
    val scanToken: String? = null,
    val manualCode: String? = null,
    val deviceId: String? = null
)
