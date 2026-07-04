package profile.domain

import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

@Serializable
data class SocialLink(
    val label: String,
    val url: String
)

@Serializable
data class UserProfileMetadata(
    val socialLinks: List<SocialLink> = emptyList()
)

@Serializable
data class BirthdayParts(
    val day: Int,
    val month: Int
)

enum class VisibilityAudience(val wireValue: String) {
    PUBLIC("public"),
    FOLLOWERS("followers"),
    FRIENDS("friends"),
    PRIVATE("private");

    companion object {
        fun fromWire(value: String?): VisibilityAudience? {
            return entries.firstOrNull { it.wireValue == value?.trim()?.lowercase(Locale.ROOT) }
        }
    }
}

@Serializable
data class FieldVisibilityResponse(
    val bio: String = VisibilityAudience.PUBLIC.wireValue,
    val birthday: String = VisibilityAudience.PRIVATE.wireValue,
    val socialLinks: String = VisibilityAudience.PUBLIC.wireValue
)

data class FieldVisibility(
    val bio: VisibilityAudience = VisibilityAudience.PUBLIC,
    val birthday: VisibilityAudience = VisibilityAudience.PRIVATE,
    val socialLinks: VisibilityAudience = VisibilityAudience.PUBLIC
) {
    fun toResponse() = FieldVisibilityResponse(
        bio = bio.wireValue,
        birthday = birthday.wireValue,
        socialLinks = socialLinks.wireValue
    )

    companion object {
        fun fromResponse(value: FieldVisibilityResponse?): FieldVisibility {
            return FieldVisibility(
                bio = VisibilityAudience.fromWire(value?.bio) ?: VisibilityAudience.PUBLIC,
                birthday = VisibilityAudience.fromWire(value?.birthday) ?: VisibilityAudience.PRIVATE,
                socialLinks = VisibilityAudience.fromWire(value?.socialLinks) ?: VisibilityAudience.PUBLIC
            )
        }
    }
}

object ProfileVisibility {
    fun canView(
        ownerId: UUID,
        viewerId: UUID?,
        relationship: Relationship,
        privacy: PrivacySettings,
        audience: VisibilityAudience
    ): Boolean {
        if (viewerId == ownerId) return true
        if (relationship.isBlocked) return false
        return when (audience) {
            VisibilityAudience.PUBLIC -> !privacy.isPrivate || relationship.isFollowing
            VisibilityAudience.FOLLOWERS -> relationship.isFollowing
            VisibilityAudience.FRIENDS -> relationship.isFriend
            VisibilityAudience.PRIVATE -> false
        }
    }
}
