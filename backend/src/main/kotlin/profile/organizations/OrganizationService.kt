package profile.organizations

import profile.domain.*
import profile.infrastructure.db.OrganizationRepository
import profile.infrastructure.db.UserRepository
import profile.users.toPublicDto
import java.util.UUID

class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository
) {
    fun create(userId: String, request: CreateOrganizationRequest): OrganizationDto {
        val orgName = normalizeOrgName(request.orgName)
        require(orgName.length >= 3) { "Organization name must be at least 3 characters" }
        require(organizationRepository.findByName(orgName) == null) { "Organization name is already used" }
        val displayName = request.displayName.trim().takeIf(String::isNotBlank) ?: orgName
        val org = organizationRepository.create(
            Organization(
                orgName = orgName,
                displayName = displayName.take(120),
                bio = request.bio?.trim()?.take(500)?.takeIf(String::isNotBlank),
                createdByUserId = userId
            )
        )
        return org.toDto(OrganizationRole.OWNER)
    }

    fun update(userId: String, orgId: String, request: UpdateOrganizationRequest): OrganizationDto {
        requireRole(userId, orgId, OrganizationRole.OWNER)
        val current = organizationRepository.findById(orgId) ?: throw IllegalArgumentException("Organization not found")
        val updated = organizationRepository.update(
            orgId = orgId,
            displayName = request.displayName?.trim()?.takeIf(String::isNotBlank)?.take(120) ?: current.displayName,
            bio = if (request.bioProvided) request.bio?.trim()?.take(500)?.takeIf(String::isNotBlank) else current.bio,
            socialLinks = request.socialLinks?.let(::normalizeSocialLinks) ?: current.socialLinks,
            avatarUrl = current.avatarUrl
        )
        return updated.toDto(OrganizationRole.OWNER)
    }

    fun context(userId: String, activeOwner: OwnerRef): OrganizationContextResponse {
        val user = userRepository.findById(userId) ?: throw IllegalArgumentException("User not found")
        val organizations = organizationRepository.listForUser(userId)
        val active = if (activeOwner.type == OwnerType.ORGANIZATION) {
            organizations.firstOrNull { it.first.id == activeOwner.id.toString() }?.let { it.first.toOwnerIdentity(it.second) }
                ?: user.toPublicDto().let {
                    OwnerIdentityDto(OwnerType.USER, user.id, user.username, listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { user.username }, user.avatarUrl)
                }
        } else {
            OwnerIdentityDto(OwnerType.USER, user.id, user.username, listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { user.username }, user.avatarUrl)
        }
        return OrganizationContextResponse(
            activeOwner = active,
            organizations = organizations.map { it.first.toDto(it.second) },
            pendingInvitations = pendingInvitations(userId)
        )
    }

    fun listMine(userId: String): List<OrganizationDto> =
        organizationRepository.listForUser(userId).map { it.first.toDto(it.second) }

    fun findByName(name: String): OrganizationDto? =
        organizationRepository.findByName(name)?.toDto()

    fun search(query: String, limit: Int): List<OrganizationDto> =
        organizationRepository.search(query, limit).map { it.toDto() }

    fun findOwner(ref: OwnerRef): OwnerIdentityDto? =
        when (ref.type) {
            OwnerType.USER -> userRepository.findById(ref.id.toString())?.let { user ->
                OwnerIdentityDto(
                    ownerType = OwnerType.USER,
                    ownerId = user.id,
                    username = user.username,
                    displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { user.username },
                    avatarUrl = user.avatarUrl
                )
            }
            OwnerType.ORGANIZATION -> organizationRepository.findById(ref.id.toString())?.toOwnerIdentity()
        }

    fun invite(userId: String, orgId: String, request: InviteOrganizationMemberRequest): OrganizationInvitationDto {
        requireRole(userId, orgId, OrganizationRole.OWNER)
        val invited = request.userId?.let { userRepository.findById(it) }
            ?: request.username?.let { userRepository.findByUsername(it) }
            ?: throw IllegalArgumentException("Invited user not found")
        val role = request.role ?: OrganizationRole.CONTRIBUTOR
        require(role in setOf(OrganizationRole.OWNER, OrganizationRole.CONTRIBUTOR)) { "Invalid role" }
        if (organizationRepository.member(orgId, invited.id) != null) {
            throw IllegalArgumentException("User is already a member")
        }
        val invitation = organizationRepository.createInvitation(
            OrganizationInvitation(
                organizationId = orgId,
                invitedUserId = invited.id,
                invitedByUserId = userId,
                role = role
            )
        )
        return invitation.toDto()
    }

    fun pendingInvitations(userId: String): List<OrganizationInvitationDto> =
        organizationRepository.invitationsForUser(userId).map { it.toDto() }

    fun acceptInvitation(userId: String, invitationId: String): OrganizationInvitationDto {
        val invitation = requireInvitation(userId, invitationId)
        organizationRepository.upsertMember(invitation.organizationId, userId, invitation.role)
        organizationRepository.updateInvitationStatus(invitation.id, OrganizationInvitationStatus.ACCEPTED)
        return invitation.copy(status = OrganizationInvitationStatus.ACCEPTED).toDto()
    }

    fun declineInvitation(userId: String, invitationId: String): OrganizationInvitationDto {
        val invitation = requireInvitation(userId, invitationId)
        organizationRepository.updateInvitationStatus(invitation.id, OrganizationInvitationStatus.DECLINED)
        return invitation.copy(status = OrganizationInvitationStatus.DECLINED).toDto()
    }

    fun members(userId: String, orgId: String): List<OrganizationMemberDto> {
        requireMember(userId, orgId)
        return organizationRepository.members(orgId).map { member ->
            val user = userRepository.findById(member.userId)
            OrganizationMemberDto(
                organizationId = member.organizationId,
                userId = member.userId,
                username = user?.username.orEmpty(),
                firstName = user?.firstName,
                lastName = user?.lastName,
                avatarUrl = user?.avatarUrl,
                role = member.role,
                createdAt = member.createdAt.toString()
            )
        }
    }

    fun updateMember(userId: String, orgId: String, memberUserId: String, role: OrganizationRole) {
        requireRole(userId, orgId, OrganizationRole.OWNER)
        val current = organizationRepository.member(orgId, memberUserId) ?: throw IllegalArgumentException("Member not found")
        if (current.role == OrganizationRole.OWNER && role != OrganizationRole.OWNER && organizationRepository.ownerCount(orgId) <= 1) {
            throw IllegalArgumentException("Organization must have at least one owner")
        }
        organizationRepository.upsertMember(orgId, memberUserId, role)
    }

    fun removeMember(userId: String, orgId: String, memberUserId: String) {
        requireRole(userId, orgId, OrganizationRole.OWNER)
        val current = organizationRepository.member(orgId, memberUserId) ?: return
        if (current.role == OrganizationRole.OWNER && organizationRepository.ownerCount(orgId) <= 1) {
            throw IllegalArgumentException("Organization must have at least one owner")
        }
        organizationRepository.removeMember(orgId, memberUserId)
    }

    fun authorize(userId: String, owner: OwnerRef, action: OwnerAction): Boolean {
        if (owner.type == OwnerType.USER) return owner.id.toString() == userId
        val member = organizationRepository.member(owner.id.toString(), userId) ?: return false
        return when (action) {
            OwnerAction.CREATE_CONTENT,
            OwnerAction.ACT_AS_OWNER -> member.role in setOf(OrganizationRole.OWNER, OrganizationRole.CONTRIBUTOR)
            OwnerAction.MANAGE_ORGANIZATION,
            OwnerAction.MANAGE_MEMBERS -> member.role == OrganizationRole.OWNER
        }
    }

    fun requireSwitchAllowed(userId: String, owner: OwnerRef) {
        if (owner.type == OwnerType.USER) {
            require(owner.id.toString() == userId) { "Cannot switch to another user" }
            return
        }
        require(organizationRepository.member(owner.id.toString(), userId) != null) { "Organization membership is required" }
    }

    fun role(userId: String, orgId: String): OrganizationRole? =
        organizationRepository.member(orgId, userId)?.role

    private fun requireInvitation(userId: String, invitationId: String): OrganizationInvitation {
        val invitation = organizationRepository.findInvitation(invitationId)
            ?: throw IllegalArgumentException("Invitation not found")
        require(invitation.invitedUserId == userId) { "Invitation is not for current user" }
        require(invitation.status == OrganizationInvitationStatus.PENDING) { "Invitation is not pending" }
        return invitation
    }

    private fun OrganizationInvitation.toDto(): OrganizationInvitationDto {
        val org = organizationRepository.findById(organizationId) ?: throw IllegalArgumentException("Organization not found")
        return OrganizationInvitationDto(
            id = id,
            organization = org.toDto(organizationRepository.member(organizationId, invitedUserId)?.role),
            invitedUserId = invitedUserId,
            invitedByUserId = invitedByUserId,
            role = role,
            status = status,
            createdAt = createdAt.toString()
        )
    }

    private fun requireMember(userId: String, orgId: String): OrganizationMember =
        organizationRepository.member(orgId, userId) ?: throw IllegalArgumentException("Organization membership is required")

    private fun requireRole(userId: String, orgId: String, role: OrganizationRole): OrganizationMember {
        val member = requireMember(userId, orgId)
        require(member.role == role) { "Organization owner role is required" }
        return member
    }

    private fun normalizeOrgName(value: String): String {
        val normalized = value.trim().lowercase()
        require(normalized.matches(Regex("[a-z0-9][a-z0-9_-]{2,39}"))) { "Invalid organization name" }
        return normalized
    }

    private fun normalizeSocialLinks(links: List<SocialLink>): List<SocialLink> =
        links.mapNotNull { link ->
            val label = link.label.trim().take(60)
            val url = link.url.trim().take(500)
            if (label.isBlank() && url.isBlank()) null else SocialLink(label = label, url = url)
        }.take(10)
}

enum class OwnerAction { CREATE_CONTENT, MANAGE_ORGANIZATION, MANAGE_MEMBERS, ACT_AS_OWNER }

@kotlinx.serialization.Serializable
data class CreateOrganizationRequest(
    val orgName: String,
    val displayName: String,
    val bio: String? = null
)

@kotlinx.serialization.Serializable
data class UpdateOrganizationRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val socialLinks: List<SocialLink>? = null,
    @kotlinx.serialization.Transient val bioProvided: Boolean = false
)

@kotlinx.serialization.Serializable
data class InviteOrganizationMemberRequest(
    val username: String? = null,
    val userId: String? = null,
    val role: OrganizationRole? = OrganizationRole.CONTRIBUTOR
)

@kotlinx.serialization.Serializable
data class UpdateOrganizationMemberRequest(val role: OrganizationRole)

@kotlinx.serialization.Serializable
data class SwitchOwnerRequest(val ownerType: OwnerType, val ownerId: String)

@kotlinx.serialization.Serializable
data class OrganizationContextResponse(
    val activeOwner: OwnerIdentityDto,
    val organizations: List<OrganizationDto> = emptyList(),
    val pendingInvitations: List<OrganizationInvitationDto> = emptyList()
)
