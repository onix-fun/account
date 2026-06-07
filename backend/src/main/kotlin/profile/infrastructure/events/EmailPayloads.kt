package profile.infrastructure.events

import kotlinx.serialization.Serializable

@Serializable
data class VerificationEmailPayload(
    val email: String,
    val code: String,
    val locale: EmailLocale = EmailLocale.EN
)

@Serializable
data class PasswordResetEmailPayload(
    val email: String,
    val code: String,
    val locale: EmailLocale = EmailLocale.EN
)

@Serializable
data class SecurityNotificationPayload(
    val email: String,
    val type: SecurityNoticeType,
    val locale: EmailLocale = EmailLocale.EN
)
