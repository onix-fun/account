package profile.users

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import profile.domain.BirthdayParts
import profile.domain.SocialLink
import profile.infrastructure.db.User

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val bio: String? = null,
    val birthDate: String? = null,
    val socialLinks: List<SocialLink>? = null,
    @Transient val birthDateProvided: Boolean = false,
    @Transient val socialLinksProvided: Boolean = false
) {
    companion object {
        fun fromJson(body: JsonObject): UpdateProfileRequest {
            return UpdateProfileRequest(
                username = body.stringOrNull("username"),
                firstName = body.stringOrNull("firstName"),
                lastName = body.stringOrNull("lastName"),
                bio = body.stringOrNull("bio"),
                birthDate = body.stringOrNull("birthDate"),
                socialLinks = (body["socialLinks"] as? JsonArray)?.mapNotNull { item ->
                    val link = item as? JsonObject ?: return@mapNotNull null
                    SocialLink(
                        label = link.stringOrNull("label").orEmpty(),
                        url = link.stringOrNull("url").orEmpty()
                    )
                },
                birthDateProvided = body.containsKey("birthDate"),
                socialLinksProvided = body.containsKey("socialLinks")
            )
        }

        private fun JsonObject.stringOrNull(key: String): String? {
            val value = this[key] ?: return null
            if (value is JsonNull) return null
            return value.jsonPrimitive.contentOrNull
        }
    }
}

@Serializable
data class UserPublicDto(
    val id: String,
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val birthday: BirthdayParts? = null,
    val socialLinks: List<SocialLink> = emptyList()
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
    val birthDate: String? = null,
    val birthday: BirthdayParts? = null,
    val socialLinks: List<SocialLink> = emptyList(),
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
    birthday = birthDate?.toBirthdayParts(),
    socialLinks = socialLinks
)

@Serializable
data class RequestEmailChangeRequest(val currentPassword: String, val newEmail: String)

@Serializable
data class ConfirmEmailChangeRequest(val code: String)

fun User.toProfileDto() = UserProfileDto(
    id = id,
    email = email,
    username = username,
    emailVerified = emailVerified,
    firstName = firstName,
    lastName = lastName,
    avatarUrl = avatarUrl,
    bio = bio,
    birthDate = birthDate,
    birthday = birthDate?.toBirthdayParts(),
    socialLinks = socialLinks,
    role = role,
    status = status
)

fun String.toBirthdayParts(): BirthdayParts? {
    val parts = split("-")
    if (parts.size != 3) return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return BirthdayParts(day = day, month = month)
}
