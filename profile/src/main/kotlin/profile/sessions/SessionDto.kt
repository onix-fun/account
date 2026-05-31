package profile.sessions

import kotlinx.serialization.Serializable
import profile.users.UserPublicDto

@Serializable
data class SessionInfoDto(
    val id: String,
    val userId: String,
    val isCurrent: Boolean = false,
    val deviceId: String? = null,
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val lastUsedAt: String,
    val expiresAt: String,
    val createdAt: String,
    val user: UserPublicDto? = null
)
