package profile.auth

import kotlinx.serialization.Serializable
import profile.users.UserProfileDto

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null
)

@Serializable
data class LoginRequest(
    val identifier: String? = null,
    val email: String? = null,
    val password: String,
    val deviceId: String? = null
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val userId: String,
    val user: UserProfileDto
)

@Serializable
data class RegistrationStartedResponse(
    val email: String,
    val expiresInSeconds: Long,
    val message: String
)

@Serializable
data class ConfirmRegistrationRequest(
    val email: String,
    val code: String,
    val deviceId: String? = null
)

@Serializable
data class ResendRegistrationCodeRequest(
    val email: String
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
