package profile.infrastructure.events

import kotlinx.serialization.Serializable

@Serializable
data class VerificationEmailPayload(
    val email: String,
    val code: String
)

@Serializable
data class PasswordResetEmailPayload(
    val email: String,
    val code: String
)

@Serializable
data class SecurityNotificationPayload(val email: String, val message: String)
