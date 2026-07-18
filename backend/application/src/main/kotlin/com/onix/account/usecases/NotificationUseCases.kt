package com.onix.account.usecases

import com.onix.account.domain.*
import java.time.Instant
import java.util.UUID

interface NotificationRepository {
    fun save(n: Notification)
    fun existsBySourceEventId(eventId: String): Boolean
    fun findByRecipient(recipientId: UUID, offset: Int, limit: Int): Pair<List<Notification>, Int>
    fun countUnread(recipientId: UUID): Int
    fun markRead(id: UUID)
    fun markAllRead(recipientId: UUID)
    fun getPrefs(userId: UUID): NotificationPrefs
    fun savePrefs(prefs: NotificationPrefs)
    fun registerCatalog(catalog: NotificationServiceCatalog)
    fun activateServiceForUser(userId: UUID, serviceKey: String)
    fun notificationTypeExists(serviceKey: String, typeKey: String): Boolean
    fun notificationTypeEnabled(userId: UUID, serviceKey: String, typeKey: String, owner: OwnerRef? = null): Boolean
    fun getLocalizedSettings(userId: UUID, locale: String, owner: OwnerRef? = null): List<LocalizedNotificationServiceSettings>
    fun savePreference(userId: UUID, serviceKey: String, typeKey: String, enabled: Boolean)
    fun saveOwnerPreference(userId: UUID, owner: OwnerRef, serviceKey: String, typeKey: String, enabled: Boolean)
}

interface NotificationOutboxRepository {
    fun enqueue(event: UserActivityEvent): Boolean
}

interface NotificationOutboxProcessor {
    fun processPending(limit: Int, handler: (NotificationOutboxItem) -> Unit): Int
}

class NotificationUseCases(
    private val repo: NotificationRepository,
    private val outboxRepo: NotificationOutboxRepository
) {

    fun createFromEvent(
        eventId: String,
        recipientId: UUID,
        type: String,
        title: String,
        body: String,
        actorId: UUID? = null,
        sourceOwner: OwnerRef? = actorId?.let { OwnerRef.user(it) },
        targetOwner: OwnerRef? = null,
        entityType: String? = null,
        entityId: String? = null,
        metadataJson: String = "{}"
    ): Notification? {
        if (eventId.isNotBlank() && repo.existsBySourceEventId(eventId)) return null
        val serviceKey = scopedService(type)
        val typeKey = scopedType(type)
        if (repo.notificationTypeExists(serviceKey, typeKey)) {
            repo.activateServiceForUser(recipientId, serviceKey)
            if (!repo.notificationTypeEnabled(recipientId, serviceKey, typeKey, notificationPreferenceOwner(sourceOwner, targetOwner))) return null
        }

        val notif = Notification(
            recipientId = recipientId, type = scopedTypeName(serviceKey, typeKey),
            serviceKey = serviceKey,
            typeKey = typeKey,
            title = title, body = body,
            metadataJson = metadataJson, actorId = actorId, entityType = entityType,
            sourceOwner = sourceOwner,
            targetOwner = targetOwner,
            entityId = entityId, sourceEventId = eventId.ifBlank { null }, createdAt = Instant.now()
        )
        repo.save(notif)
        return notif
    }

    fun getNotifications(recipientId: UUID, page: Int, limit: Int): NotificationPage {
        val (items, total) = repo.findByRecipient(recipientId, (page - 1) * limit, limit)
        return NotificationPage(items, total)
    }

    fun getUnreadCount(recipientId: UUID): Int = repo.countUnread(recipientId)

    fun markRead(id: UUID) = repo.markRead(id)
    fun markAllRead(recipientId: UUID) = repo.markAllRead(recipientId)

    fun getPrefs(userId: UUID): NotificationPrefs = repo.getPrefs(userId)

    fun savePrefs(prefs: NotificationPrefs) = repo.savePrefs(prefs)

    fun registerCatalog(catalog: NotificationServiceCatalog) = repo.registerCatalog(catalog)

    fun activateServiceForUser(userId: UUID, serviceKey: String) = repo.activateServiceForUser(userId, serviceKey)

    fun getLocalizedSettings(userId: UUID, locale: String, owner: OwnerRef? = null): List<LocalizedNotificationServiceSettings> {
        repo.activateServiceForUser(userId, "account")
        return repo.getLocalizedSettings(userId, locale, owner)
    }

    fun savePreference(userId: UUID, serviceKey: String, typeKey: String, enabled: Boolean) =
        repo.savePreference(userId, serviceKey, typeKey, enabled)

    fun saveOwnerPreference(userId: UUID, owner: OwnerRef, serviceKey: String, typeKey: String, enabled: Boolean) =
        repo.saveOwnerPreference(userId, owner, serviceKey, typeKey, enabled)

    fun sendToUser(
        sourceEventId: String,
        recipientId: UUID,
        serviceKey: String,
        typeKey: String,
        title: LocalizedText,
        body: LocalizedText,
        actorId: UUID? = null,
        sourceOwner: OwnerRef? = actorId?.let { OwnerRef.user(it) },
        targetOwner: OwnerRef? = null,
        entityType: String? = null,
        entityId: String? = null,
        metadataJson: String = "{}"
    ): Notification? {
        require(sourceEventId.isNotBlank()) { "source_event_id is required" }
        require(serviceKey.isNotBlank()) { "service_key is required" }
        require(typeKey.isNotBlank()) { "type_key is required" }
        require(repo.notificationTypeExists(serviceKey, typeKey)) { "notification type is not registered" }
        if (repo.existsBySourceEventId(sourceEventId)) return null
        repo.activateServiceForUser(recipientId, serviceKey)
        if (!repo.notificationTypeEnabled(recipientId, serviceKey, typeKey, notificationPreferenceOwner(sourceOwner, targetOwner))) return null
        val notif = Notification(
            recipientId = recipientId,
            type = scopedTypeName(serviceKey, typeKey),
            serviceKey = serviceKey,
            typeKey = typeKey,
            title = title.en.ifBlank { title.ru },
            body = body.en.ifBlank { body.ru },
            titleI18nJson = localizedJson(title),
            bodyI18nJson = localizedJson(body),
            metadataJson = metadataJson.ifBlank { "{}" },
            actorId = actorId,
            sourceOwner = sourceOwner,
            targetOwner = targetOwner,
            entityType = entityType,
            entityId = entityId,
            sourceEventId = sourceEventId,
            createdAt = Instant.now()
        )
        repo.save(notif)
        return notif
    }

    fun publishUserActivity(
        sourceEventId: String,
        actorId: UUID,
        activityType: UserActivityType,
        entityType: String?,
        entityId: String?,
        metadataJson: String,
        actorType: OwnerType = OwnerType.USER
    ): PublishActivityResult {
        require(sourceEventId.isNotBlank()) { "source_event_id is required" }
        val event = UserActivityEvent(
            sourceEventId = sourceEventId,
            actorId = actorId,
            actorType = actorType,
            activityType = activityType,
            entityType = entityType?.takeIf { it.isNotBlank() },
            entityId = entityId?.takeIf { it.isNotBlank() },
            metadataJson = metadataJson.ifBlank { "{}" }
        )
        val inserted = outboxRepo.enqueue(event)
        return PublishActivityResult(
            status = if (inserted) PublishActivityStatus.ACCEPTED else PublishActivityStatus.DUPLICATE,
            sourceEventId = sourceEventId
        )
    }

    fun createActivityNotification(event: UserActivityEvent, recipientId: UUID): Notification? {
        if (recipientId == event.actorId) return null
        val copy = NotificationTemplates.forActivity(event.activityType)
        return createFromEvent(
            eventId = "${event.sourceEventId}:recipient:$recipientId",
            recipientId = recipientId,
            type = copy.type,
            title = copy.title,
            body = copy.body,
            actorId = event.actorId,
            sourceOwner = OwnerRef(event.actorType, event.actorId),
            targetOwner = OwnerRef(event.actorType, event.actorId),
            entityType = event.entityType,
            entityId = event.entityId,
            metadataJson = event.metadataJson
        )
    }

    fun createSubscriptionCreatedNotification(sub: Subscription): Notification? {
        val pending = sub.status == SubscriptionStatus.PENDING
        val metadataJson = if (pending) {
            """{"actions":[{"kind":"accept_follow","targetUserId":"${sub.subscriberId}"},{"kind":"reject_follow","targetUserId":"${sub.subscriberId}"}]}"""
        } else {
            "{}"
        }
        return createFromEvent(
            eventId = "subscription.created:${sub.subscriberId}:${sub.subscribedToId}:${sub.status}",
            recipientId = sub.subscribedToId,
            type = if (pending) "account.subscription_request" else "account.subscription_accepted",
            title = if (pending) "New subscription request" else "New subscriber",
            body = if (pending) "Someone wants to subscribe to you" else "Someone subscribed to you",
            actorId = sub.subscriberId,
            sourceOwner = OwnerRef(sub.subscriberType, sub.subscriberId),
            targetOwner = OwnerRef(sub.subscribedToType, sub.subscribedToId),
            entityType = sub.subscriberType.name.lowercase(),
            entityId = sub.subscriberId.toString(),
            metadataJson = metadataJson
        )
    }

    fun createSubscriptionAcceptedNotification(sub: Subscription): Notification? {
        return createFromEvent(
            eventId = "subscription.accepted:${sub.subscriberId}:${sub.subscribedToId}",
            recipientId = sub.subscriberId,
            type = "account.subscription_accepted",
            title = "Request accepted",
            body = "Your subscription request was accepted",
            actorId = sub.subscribedToId,
            sourceOwner = OwnerRef(sub.subscribedToType, sub.subscribedToId),
            targetOwner = OwnerRef(sub.subscriberType, sub.subscriberId),
            entityType = sub.subscribedToType.name.lowercase(),
            entityId = sub.subscribedToId.toString()
        )
    }

    fun createOrganizationInvitationNotification(
        invitation: OrganizationInvitation,
        organization: OrganizationDto
    ): Notification? {
        val organizationOwner = OwnerRef.organization(UUID.fromString(invitation.organizationId))
        return createFromEvent(
            eventId = "organization.invitation:${invitation.id}",
            recipientId = UUID.fromString(invitation.invitedUserId),
            type = "account.organization_invitation",
            title = "Organization invitation",
            body = "You were invited to ${organization.displayName}",
            actorId = UUID.fromString(invitation.invitedByUserId),
            sourceOwner = organizationOwner,
            targetOwner = organizationOwner,
            entityType = "organization",
            entityId = invitation.organizationId,
            metadataJson = """{"titleKey":"organizationInvitation","bodyKey":"organizationInvitation","organizationName":${jsonString(organization.displayName)},"organizationUsername":${jsonString(organization.orgName)},"role":${jsonString(invitation.role.name)},"actions":[{"kind":"accept_organization_invitation","invitationId":"${invitation.id}"},{"kind":"decline_organization_invitation","invitationId":"${invitation.id}"}]}"""
        )
    }

    fun createBirthdayNotification(recipientId: UUID, birthdayUserId: UUID, dateKey: String): Notification? {
        return createFromEvent(
            eventId = "birthday:$recipientId:$birthdayUserId:$dateKey",
            recipientId = recipientId,
            type = "account.birthday_today",
            title = "Birthday today",
            body = "Someone you follow has a birthday today",
            actorId = birthdayUserId,
            sourceOwner = OwnerRef.user(birthdayUserId),
            targetOwner = OwnerRef.user(recipientId),
            entityType = "user",
            entityId = birthdayUserId.toString(),
            metadataJson = """{"titleKey":"birthdayToday","bodyKey":"birthdayToday"}"""
        )
    }
}

data class NotificationTemplate(
    val type: String,
    val title: String,
    val body: String
)

object NotificationTemplates {
    fun forActivity(type: UserActivityType): NotificationTemplate = when (type) {
        UserActivityType.POST_PUBLISHED -> NotificationTemplate(
            type = "content.post_published",
            title = "New publication",
            body = "Someone you follow published a new post"
        )
        UserActivityType.STORY_PUBLISHED -> NotificationTemplate(
            type = "content.story_published",
            title = "New story",
            body = "Someone you follow added a new story"
        )
        UserActivityType.AUTHOR_MENTION -> NotificationTemplate(
            type = "content.author_mention",
            title = "New mention",
            body = "You were mentioned as an author"
        )
        UserActivityType.POST_COMMENT -> NotificationTemplate(
            type = "content.post_comment",
            title = "New comment",
            body = "Someone commented on your post"
        )
    }
}

private fun scopedService(type: String): String =
    type.substringBefore('.', if (type in accountTypeKeys) "account" else "content")

private fun scopedType(type: String): String = type.substringAfter('.', type)

private fun scopedTypeName(serviceKey: String, typeKey: String): String = "$serviceKey.$typeKey"

private fun notificationPreferenceOwner(sourceOwner: OwnerRef?, targetOwner: OwnerRef?): OwnerRef? =
    listOfNotNull(targetOwner, sourceOwner).firstOrNull { it.type == OwnerType.ORGANIZATION }

private val accountTypeKeys = setOf("subscription_request", "subscription_accepted", "birthday_today", "organization_invitation")

private fun localizedJson(text: LocalizedText): String =
    """{"ru":${jsonString(text.ru)},"en":${jsonString(text.en)}}"""

private fun jsonString(value: String): String =
    kotlinx.serialization.json.JsonPrimitive(value).toString()
