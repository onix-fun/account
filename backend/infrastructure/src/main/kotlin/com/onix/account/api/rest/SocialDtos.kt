package com.onix.account.api.rest

import kotlinx.serialization.Serializable
import com.onix.account.domain.Notification
import com.onix.account.domain.NotificationPrefs
import com.onix.account.domain.LocalizedNotificationServiceSettings
import com.onix.account.domain.LocalizedNotificationTypeSetting
import com.onix.account.domain.PrivacySettings
import com.onix.account.domain.Relationship
import com.onix.account.domain.BirthdayParts
import com.onix.account.domain.FieldVisibilityResponse
import com.onix.account.domain.SocialLink
import com.onix.account.domain.Subscription
import com.onix.account.users.UserPublicDto

@Serializable
data class SuccessResponse(val success: Boolean = true)

@Serializable
data class CountResponse(val count: Int)

@Serializable
data class RelationshipResponse(
    val isFollowing: Boolean,
    val isFollowedBy: Boolean,
    val isFriend: Boolean,
    val isBlocked: Boolean,
    val hasPendingRequest: Boolean
)

@Serializable
data class InternalSocialGraphResponse(
    val followingIds: List<String> = emptyList(),
    val friendIds: List<String> = emptyList(),
    val blockedIds: List<String> = emptyList()
)

@Serializable
data class PublicUserRelationshipResponse(
    val id: String,
    val ownerType: String = "USER",
    val username: String,
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val organizationMembershipState: String? = null,
    val relationship: RelationshipResponse? = null
)

@Serializable
data class ProfileSummaryResponse(
    val id: String,
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val bio: String? = null,
    val birthDate: String? = null,
    val birthday: BirthdayParts? = null,
    val socialLinks: List<SocialLink> = emptyList(),
    val avatarUrl: String? = null,
    val followersCount: Long,
    val followingCount: Long,
    val isPrivate: Boolean,
    val unreadNotificationCount: Int,
    val pendingRequestsCount: Int
)

@Serializable
data class PublicProfileResponse(
    val id: String,
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val bio: String? = null,
    val birthday: BirthdayParts? = null,
    val socialLinks: List<SocialLink> = emptyList(),
    val avatarUrl: String? = null,
    val followersCount: Long,
    val followingCount: Long,
    val isPrivate: Boolean,
    val relationship: RelationshipResponse
)

@Serializable
data class UserPageResponse(
    val items: List<PublicUserRelationshipResponse>,
    val totalCount: Int
)

@Serializable
data class SubscriptionResponse(
    val id: String,
    val subscriberId: String,
    val subscribedToId: String,
    val status: String,
    val isCloseFriend: Boolean,
    val createdAt: String,
    val subscriber: UserPublicDto? = null
)

@Serializable
data class SubscriptionPageResponse(
    val items: List<SubscriptionResponse>,
    val totalCount: Int
)

@Serializable
data class PrivacySettingsResponse(
    val isPrivate: Boolean,
    val fieldVisibility: FieldVisibilityResponse = FieldVisibilityResponse()
)

@Serializable
data class InternalVisibilityResponse(
    val ownerId: String,
    val viewerId: String?,
    val isPrivate: Boolean,
    val relationship: RelationshipResponse,
    val isBlocked: Boolean,
    val isCloseFriend: Boolean
)

@Serializable
data class PrivacySettingsUpdateRequest(
    val isPrivate: Boolean = false,
    val fieldVisibility: FieldVisibilityResponse? = null
)

@Serializable
data class NotificationPrefsResponse(
    val inAppSubscriptions: Boolean,
    val inAppPublications: Boolean,
    val inAppAuthorMentions: Boolean,
    val inAppPostComments: Boolean,
    val inAppNewStories: Boolean,
    val inAppBirthdays: Boolean
)

@Serializable
data class NotificationSettingsResponse(
    val services: List<NotificationServiceSettingsResponse>
)

@Serializable
data class NotificationServiceSettingsResponse(
    val serviceKey: String,
    val name: String,
    val description: String,
    val icon: String,
    val items: List<NotificationTypeSettingsResponse>
)

@Serializable
data class NotificationTypeSettingsResponse(
    val serviceKey: String,
    val typeKey: String,
    val name: String,
    val description: String,
    val icon: String,
    val enabled: Boolean
)

@Serializable
data class NotificationPreferenceUpdateRequest(
    val serviceKey: String,
    val typeKey: String,
    val enabled: Boolean,
    val ownerType: String? = null,
    val ownerId: String? = null
)

@Serializable
data class NotificationPageResponse(
    val items: List<NotificationResponse>,
    val totalCount: Int
)

@Serializable
data class NotificationResponse(
    val id: String,
    val type: String,
    val serviceKey: String,
    val typeKey: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val actorId: String? = null,
    val sourceOwnerType: String? = null,
    val sourceOwnerId: String? = null,
    val targetOwnerType: String? = null,
    val targetOwnerId: String? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val metadataJson: String,
    val metadata: NotificationMetadataResponse,
    val createdAt: String
)

@Serializable
data class NotificationMetadataResponse(
    val href: String? = null,
    val titleKey: String? = null,
    val bodyKey: String? = null,
    val actions: List<NotificationActionResponse> = emptyList()
)

@Serializable
data class NotificationActionResponse(
    val kind: String,
    val targetUserId: String? = null,
    val invitationId: String? = null,
    val href: String? = null
)

fun Relationship.toResponse() = RelationshipResponse(
    isFollowing = isFollowing,
    isFollowedBy = isFollowedBy,
    isFriend = isFriend,
    isBlocked = isBlocked,
    hasPendingRequest = hasPendingRequest
)

fun UserPublicDto.withRelationship(relationship: RelationshipResponse? = null) = PublicUserRelationshipResponse(
    id = id,
    ownerType = "USER",
    username = username,
    displayName = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { username },
    firstName = firstName,
    lastName = lastName,
    avatarUrl = avatarUrl,
    bio = bio,
    relationship = relationship
)

fun Subscription.toResponse(subscriber: UserPublicDto? = null) = SubscriptionResponse(
    id = id.toString(),
    subscriberId = subscriberId.toString(),
    subscribedToId = subscribedToId.toString(),
    status = status.name,
    isCloseFriend = isCloseFriend,
    createdAt = createdAt.toString(),
    subscriber = subscriber
)

fun PrivacySettings.toResponse() = PrivacySettingsResponse(
    isPrivate = isPrivate,
    fieldVisibility = fieldVisibility.toResponse()
)

fun NotificationPrefs.toResponse() = NotificationPrefsResponse(
    inAppSubscriptions = inAppSubscriptions,
    inAppPublications = inAppPublications,
    inAppAuthorMentions = inAppAuthorMentions,
    inAppPostComments = inAppPostComments,
    inAppNewStories = inAppNewStories,
    inAppBirthdays = inAppBirthdays
)

fun LocalizedNotificationServiceSettings.toResponse() = NotificationServiceSettingsResponse(
    serviceKey = serviceKey,
    name = name,
    description = description,
    icon = icon,
    items = items.map { it.toResponse() }
)

fun LocalizedNotificationTypeSetting.toResponse() = NotificationTypeSettingsResponse(
    serviceKey = serviceKey,
    typeKey = typeKey,
    name = name,
    description = description,
    icon = icon,
    enabled = enabled
)

fun Notification.toResponse(metadata: NotificationMetadataResponse) = NotificationResponse(
    id = id.toString(),
    type = type,
    serviceKey = serviceKey,
    typeKey = typeKey,
    title = title,
    body = body,
    isRead = isRead,
    actorId = actorId?.toString(),
    sourceOwnerType = sourceOwner?.type?.name,
    sourceOwnerId = sourceOwner?.id?.toString(),
    targetOwnerType = targetOwner?.type?.name,
    targetOwnerId = targetOwner?.id?.toString(),
    entityType = entityType,
    entityId = entityId,
    metadataJson = metadataJson,
    metadata = metadata,
    createdAt = createdAt.toString()
)
