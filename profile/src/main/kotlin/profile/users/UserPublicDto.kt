package profile.users

import kotlinx.serialization.Serializable
import profile.infrastructure.db.User

@Serializable
data class UpdateProfileRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val bio: String? = null
)

@Serializable
data class UserPublicDto(
    val id: String,
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val emailVerified: Boolean
)

@Serializable
data class UserProfileDto(
    val id: String,
    val email: String,
    val username: String,
    val emailVerified: Boolean,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val role: String,
    val status: String
)

fun User.toPublicDto() = UserPublicDto(
    id = id,
    username = username,
    firstName = firstName,
    lastName = lastName,
    avatarUrl = avatarUrl,
    bio = bio,
    emailVerified = emailVerified
)

fun User.toProfileDto() = UserProfileDto(
    id = id,
    email = email,
    username = username,
    emailVerified = emailVerified,
    firstName = firstName,
    lastName = lastName,
    avatarUrl = avatarUrl,
    bio = bio,
    role = role,
    status = status
)
