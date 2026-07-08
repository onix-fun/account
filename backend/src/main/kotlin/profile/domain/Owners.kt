package profile.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
enum class OwnerType { USER, ORGANIZATION }

data class OwnerRef(
    val type: OwnerType,
    val id: UUID
) {
    companion object {
        fun user(id: UUID) = OwnerRef(OwnerType.USER, id)
        fun organization(id: UUID) = OwnerRef(OwnerType.ORGANIZATION, id)
    }
}

enum class OrganizationRole { OWNER, CONTRIBUTOR }

enum class OrganizationInvitationStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }

data class Organization(
    val id: String = "",
    val orgName: String,
    val displayName: String,
    val bio: String? = null,
    val socialLinks: List<SocialLink> = emptyList(),
    val avatarUrl: String? = null,
    val status: String = "ACTIVE",
    val createdByUserId: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class OrganizationMember(
    val organizationId: String,
    val userId: String,
    val role: OrganizationRole,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class OrganizationInvitation(
    val id: String = "",
    val organizationId: String,
    val invitedUserId: String,
    val invitedByUserId: String,
    val role: OrganizationRole,
    val status: OrganizationInvitationStatus = OrganizationInvitationStatus.PENDING,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null
)

@Serializable
data class OwnerIdentityDto(
    val ownerType: OwnerType,
    val ownerId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: OrganizationRole? = null
)

@Serializable
data class OrganizationDto(
    val id: String,
    val orgName: String,
    val displayName: String,
    val bio: String? = null,
    val socialLinks: List<SocialLink> = emptyList(),
    val avatarUrl: String? = null,
    val status: String = "ACTIVE",
    val role: OrganizationRole? = null,
    val createdByUserId: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class OrganizationMemberDto(
    val organizationId: String,
    val userId: String,
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val role: OrganizationRole,
    val createdAt: String
)

@Serializable
data class OrganizationInvitationDto(
    val id: String,
    val organization: OrganizationDto,
    val invitedUserId: String,
    val invitedByUserId: String,
    val role: OrganizationRole,
    val status: OrganizationInvitationStatus,
    val createdAt: String
)

fun Organization.toDto(role: OrganizationRole? = null) = OrganizationDto(
    id = id,
    orgName = orgName,
    displayName = displayName,
    bio = bio,
    socialLinks = socialLinks,
    avatarUrl = avatarUrl,
    status = status,
    role = role,
    createdByUserId = createdByUserId,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)

fun Organization.toOwnerIdentity(role: OrganizationRole? = null) = OwnerIdentityDto(
    ownerType = OwnerType.ORGANIZATION,
    ownerId = id,
    username = orgName,
    displayName = displayName,
    avatarUrl = avatarUrl,
    role = role
)
