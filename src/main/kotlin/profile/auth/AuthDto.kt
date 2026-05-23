package profile.auth

import kotlinx.serialization.Serializable

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
    val email: String, 
    val password: String,
    val deviceId: String? = null
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val userId: String,
    val user: profile.infrastructure.db.User
)

// No body needed for Refresh or Logout as they use the single HttpOnly cookie.
// But we might still want the DTOs if we use them in the routes (though they'll be empty).
// Let's remove them if they aren't needed.

@Serializable
data class VerifyEmailRequest(
    val code: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)
