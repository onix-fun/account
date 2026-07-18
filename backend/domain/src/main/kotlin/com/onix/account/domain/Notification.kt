package com.onix.account.domain

import java.time.Instant
import java.util.UUID
import com.fasterxml.uuid.Generators

data class Notification(
    val id: UUID = Generators.timeBasedEpochGenerator().generate(),
    val recipientId: UUID,
    val type: String,
    val serviceKey: String = type.substringBefore('.', "account"),
    val typeKey: String = type.substringAfter('.', type),
    val title: String,
    val body: String,
    val titleI18nJson: String? = null,
    val bodyI18nJson: String? = null,
    val metadataJson: String = "{}",
    val isRead: Boolean = false,
    val actorId: UUID? = null,
    val sourceOwner: OwnerRef? = null,
    val targetOwner: OwnerRef? = null,
    val entityType: String? = null,
    val entityId: String? = null,
    val sourceEventId: String? = null,
    val createdAt: Instant = Instant.now()
)

data class NotificationPrefs(
    val userId: UUID,
    val inAppSubscriptions: Boolean = true,
    val inAppPublications: Boolean = true,
    val inAppAuthorMentions: Boolean = true,
    val inAppPostComments: Boolean = true,
    val inAppNewStories: Boolean = true,
    val inAppBirthdays: Boolean = true,
    val updatedAt: Instant = Instant.now()
)

data class LocalizedText(
    val ru: String,
    val en: String
)

data class NotificationServiceCatalog(
    val serviceKey: String,
    val name: LocalizedText,
    val description: LocalizedText,
    val icon: String,
    val displayOrder: Int,
    val types: List<NotificationTypeCatalog>
)

data class NotificationTypeCatalog(
    val serviceKey: String,
    val typeKey: String,
    val name: LocalizedText,
    val description: LocalizedText,
    val icon: String,
    val defaultEnabled: Boolean,
    val displayOrder: Int
)

data class LocalizedNotificationTypeSetting(
    val serviceKey: String,
    val typeKey: String,
    val name: String,
    val description: String,
    val icon: String,
    val enabled: Boolean
)

data class LocalizedNotificationServiceSettings(
    val serviceKey: String,
    val name: String,
    val description: String,
    val icon: String,
    val items: List<LocalizedNotificationTypeSetting>
)

data class NotificationPage(
    val items: List<Notification>,
    val totalCount: Int
)

enum class UserActivityType {
    POST_PUBLISHED,
    STORY_PUBLISHED,
    AUTHOR_MENTION,
    POST_COMMENT
}

enum class NotificationOutboxStatus {
    PENDING,
    SENT,
    DEAD
}

data class UserActivityEvent(
    val sourceEventId: String,
    val actorType: OwnerType = OwnerType.USER,
    val actorId: UUID,
    val activityType: UserActivityType,
    val entityType: String? = null,
    val entityId: String? = null,
    val metadataJson: String = "{}",
    val createdAt: Instant = Instant.now()
)

data class NotificationOutboxItem(
    val id: UUID,
    val event: UserActivityEvent,
    val attempts: Int,
    val createdAt: Instant
)

enum class PublishActivityStatus {
    ACCEPTED,
    DUPLICATE
}

data class PublishActivityResult(
    val status: PublishActivityStatus,
    val sourceEventId: String
)
