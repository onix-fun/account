package profile.usecases

import profile.domain.*
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
        entityType: String? = null,
        entityId: String? = null,
        metadataJson: String = "{}"
    ): Notification? {
        if (eventId.isNotBlank() && repo.existsBySourceEventId(eventId)) return null
        val prefs = repo.getPrefs(recipientId)
        val allowed = when (type) {
            "post_comment" -> prefs.inAppPostComments
            "post_published" -> prefs.inAppPublications
            "story_published" -> prefs.inAppNewStories
            "author_mention" -> prefs.inAppAuthorMentions
            "subscription_request", "subscription_accepted" -> prefs.inAppSubscriptions
            "birthday_today" -> prefs.inAppBirthdays
            else -> true
        }
        if (!allowed) return null

        val notif = Notification(
            recipientId = recipientId, type = type, title = title, body = body,
            metadataJson = metadataJson, actorId = actorId, entityType = entityType,
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

    fun publishUserActivity(
        sourceEventId: String,
        actorId: UUID,
        activityType: UserActivityType,
        entityType: String?,
        entityId: String?,
        metadataJson: String
    ): PublishActivityResult {
        require(sourceEventId.isNotBlank()) { "source_event_id is required" }
        val event = UserActivityEvent(
            sourceEventId = sourceEventId,
            actorId = actorId,
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
            type = if (pending) "subscription_request" else "subscription_accepted",
            title = if (pending) "New subscription request" else "New subscriber",
            body = if (pending) "Someone wants to subscribe to you" else "Someone subscribed to you",
            actorId = sub.subscriberId,
            entityType = "user",
            entityId = sub.subscriberId.toString(),
            metadataJson = metadataJson
        )
    }

    fun createSubscriptionAcceptedNotification(sub: Subscription): Notification? {
        return createFromEvent(
            eventId = "subscription.accepted:${sub.subscriberId}:${sub.subscribedToId}",
            recipientId = sub.subscriberId,
            type = "subscription_accepted",
            title = "Request accepted",
            body = "Your subscription request was accepted",
            actorId = sub.subscribedToId,
            entityType = "user",
            entityId = sub.subscribedToId.toString()
        )
    }

    fun createBirthdayNotification(recipientId: UUID, birthdayUserId: UUID, dateKey: String): Notification? {
        return createFromEvent(
            eventId = "birthday:$recipientId:$birthdayUserId:$dateKey",
            recipientId = recipientId,
            type = "birthday_today",
            title = "Birthday today",
            body = "Someone you follow has a birthday today",
            actorId = birthdayUserId,
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
            type = "post_published",
            title = "New publication",
            body = "Someone you follow published a new post"
        )
        UserActivityType.STORY_PUBLISHED -> NotificationTemplate(
            type = "story_published",
            title = "New story",
            body = "Someone you follow added a new story"
        )
        UserActivityType.AUTHOR_MENTION -> NotificationTemplate(
            type = "author_mention",
            title = "New mention",
            body = "You were mentioned as an author"
        )
        UserActivityType.POST_COMMENT -> NotificationTemplate(
            type = "post_comment",
            title = "New comment",
            body = "Someone commented on your post"
        )
    }
}
