package profile.api.rest

import kotlinx.serialization.Serializable
import profile.domain.Notification
import profile.domain.NotificationPrefs
import profile.domain.PrivacySettings
import profile.domain.Relationship
import profile.domain.BirthdayParts
import profile.domain.FieldVisibilityResponse
import profile.domain.SocialLink
import profile.domain.Subscription
import profile.users.UserPublicDto

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
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
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
data class NotificationPageResponse(
    val items: List<NotificationResponse>,
    val totalCount: Int
)

@Serializable
data class NotificationResponse(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val actorId: String? = null,
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
    username = username,
    firstName = firstName,
    lastName = lastName,
    avatarUrl = avatarUrl,
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

fun Notification.toResponse(metadata: NotificationMetadataResponse) = NotificationResponse(
    id = id.toString(),
    type = type,
    title = title,
    body = body,
    isRead = isRead,
    actorId = actorId?.toString(),
    entityType = entityType,
    entityId = entityId,
    metadataJson = metadataJson,
    metadata = metadata,
    createdAt = createdAt.toString()
)
