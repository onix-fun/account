package profile.auth

import kotlinx.serialization.Serializable
import profile.infrastructure.db.User
import profile.users.UserProfileDto

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val preferredLocale: String? = null
)

@Serializable
data class LoginRequest(
    val identifier: String? = null,
    val email: String? = null,
    val password: String,
    val deviceId: String? = null
)

@Serializable
data class BrowserAuthResponse(
    val user: UserProfileDto
)

@Serializable
data class BrowserAccountDto(
    val id: String,
    val email: String,
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class SwitchAccountRequest(
    val userId: String
)

@Serializable
data class CsrfResponse(
    val csrfToken: String
)

@Serializable
data class UsernameAvailabilityResponse(
    val available: Boolean
)

@Serializable
data class AccountLookupResponse(
    val state: String,
    val identifier: String,
    val avatarUrl: String? = null
)

@Serializable
data class CodeSentResponse(val status: String = "CODE_SENT", val expiresInSeconds: Long = 900)

@Serializable
data class PublicVerificationRequest(
    val identifier: String
)

@Serializable
data class PublicVerificationConfirmRequest(
    val identifier: String,
    val code: String
)

@Serializable
data class TokenRefreshRequest(
    val refreshToken: String
)

@Serializable
data class ApiTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

@Serializable
data class RegistrationStartedResponse(
    val status: String = "CODE_SENT",
    val expiresInSeconds: Long
)

@Serializable
data class ConfirmRegistrationRequest(
    val email: String? = null,
    val code: String,
    val deviceId: String? = null,
    val identifier: String? = null
)

@Serializable
data class ResendRegistrationCodeRequest(
    val email: String? = null,
    val identifier: String? = null
)

@Serializable
data class VerifyEmailRequest(
    val code: String
)

@Serializable
data class ForgotPasswordRequest(
    val identifier: String? = null,
    val email: String? = null
)

@Serializable
data class ResetPasswordRequest(
    val identifier: String? = null,
    val email: String? = null,
    val code: String? = null,
    val token: String? = null,
    val newPassword: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class DeleteAccountRequest(
    val password: String
)

fun User.toBrowserAccountDto() = BrowserAccountDto(
    id = id,
    email = email,
    username = username,
    firstName = firstName,
    lastName = lastName,
    avatarUrl = avatarUrl
)
