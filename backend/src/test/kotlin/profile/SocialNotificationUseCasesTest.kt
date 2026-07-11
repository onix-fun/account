package profile

import com.unlim.profile.grpc.v1.PublishUserActivityRequest
import com.unlim.profile.grpc.v1.UserActivityType as ProtoUserActivityType
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import profile.domain.Notification
import profile.domain.NotificationServiceCatalog
import profile.domain.NotificationOutboxItem
import profile.domain.NotificationPrefs
import profile.domain.OwnerRef
import profile.domain.LocalizedNotificationServiceSettings
import profile.domain.PublishActivityStatus
import profile.domain.Subscription
import profile.domain.SubscriptionStatus
import profile.domain.UserActivityEvent
import profile.domain.UserActivityType
import profile.domain.UserBlock
import profile.domain.PrivacySettings
import profile.domain.FieldVisibility
import profile.domain.ProfileVisibility
import profile.domain.VisibilityAudience
import profile.api.grpc.SocialGrpcService
import profile.infrastructure.NotificationOutboxWorker
import profile.infrastructure.SseManager
import profile.usecases.BlockRepository
import profile.usecases.NotificationOutboxProcessor
import profile.usecases.NotificationOutboxRepository
import profile.usecases.NotificationRepository
import profile.usecases.NotificationUseCases
import profile.usecases.PrivacyRepository
import profile.usecases.SocialRepository
import profile.usecases.SocialUseCases
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialNotificationUseCasesTest {
    @Test
    fun `self follow and self block are rejected`() {
        val fixtures = socialFixtures()
        val userId = UUID.randomUUID()

        assertFailsWith<IllegalArgumentException> { fixtures.socialUseCases.subscribe(userId, userId) }
        assertFailsWith<IllegalArgumentException> { fixtures.socialUseCases.blockUser(userId, userId) }
    }

    @Test
    fun `private account creates pending subscription and public account accepts immediately`() {
        val fixtures = socialFixtures()
        val subscriberId = UUID.randomUUID()
        val privateTargetId = UUID.randomUUID()
        val publicTargetId = UUID.randomUUID()
        fixtures.privacyRepo.save(PrivacySettings(userId = privateTargetId, isPrivate = true))

        val pending = fixtures.socialUseCases.subscribe(subscriberId, privateTargetId)
        val accepted = fixtures.socialUseCases.subscribe(subscriberId, publicTargetId)

        assertEquals(SubscriptionStatus.PENDING, pending.status)
        assertEquals(SubscriptionStatus.ACCEPTED, accepted.status)
    }

    @Test
    fun `block removes subscriptions in both directions`() {
        val fixtures = socialFixtures()
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        fixtures.socialRepo.saveSubscription(Subscription(subscriberId = userA, subscribedToId = userB, status = SubscriptionStatus.ACCEPTED))
        fixtures.socialRepo.saveSubscription(Subscription(subscriberId = userB, subscribedToId = userA, status = SubscriptionStatus.ACCEPTED))

        fixtures.socialUseCases.blockUser(userA, userB)

        assertTrue(fixtures.blockRepo.isBlockedEither(userA, userB))
        assertNull(fixtures.socialRepo.findSubscription(userA, userB))
        assertNull(fixtures.socialRepo.findSubscription(userB, userA))
    }

    @Test
    fun `close friend requires accepted subscription`() {
        val fixtures = socialFixtures()
        val subscriberId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        fixtures.socialRepo.saveSubscription(Subscription(subscriberId = subscriberId, subscribedToId = targetId, status = SubscriptionStatus.PENDING))

        assertFailsWith<IllegalArgumentException> { fixtures.socialUseCases.markCloseFriend(subscriberId, targetId) }

        val pending = fixtures.socialRepo.findSubscription(subscriberId, targetId)!!
        fixtures.socialRepo.updateSubscription(pending.copy(status = SubscriptionStatus.ACCEPTED))
        assertTrue(fixtures.socialUseCases.markCloseFriend(subscriberId, targetId))
        assertTrue(fixtures.socialRepo.findSubscription(subscriberId, targetId)!!.isCloseFriend)
    }

    @Test
    fun `activity notification uses backend template and respects publication preferences`() {
        val repo = FakeNotificationRepository()
        val outbox = FakeOutboxRepository()
        val useCases = NotificationUseCases(repo, outbox)
        val actorId = UUID.randomUUID()
        val recipientId = UUID.randomUUID()
        val event = UserActivityEvent(
            sourceEventId = "post-1",
            actorId = actorId,
            activityType = UserActivityType.POST_PUBLISHED,
            entityType = "post",
            entityId = "post-1"
        )

        val notification = useCases.createActivityNotification(event, recipientId)

        assertNotNull(notification)
        assertEquals("content.post_published", notification.type)
        assertEquals("New publication", notification.title)
        repo.prefs[recipientId] = NotificationPrefs(userId = recipientId, inAppPublications = false)
        assertNull(useCases.createActivityNotification(event.copy(sourceEventId = "post-2"), recipientId))
    }

    @Test
    fun `publish user activity is idempotent by source event id`() {
        val useCases = NotificationUseCases(FakeNotificationRepository(), FakeOutboxRepository())
        val actorId = UUID.randomUUID()

        val first = useCases.publishUserActivity("activity-1", actorId, UserActivityType.STORY_PUBLISHED, "story", "story-1", "{}")
        val second = useCases.publishUserActivity("activity-1", actorId, UserActivityType.STORY_PUBLISHED, "story", "story-1", "{}")

        assertEquals(PublishActivityStatus.ACCEPTED, first.status)
        assertEquals(PublishActivityStatus.DUPLICATE, second.status)
    }

    @Test
    fun `profile field visibility respects private profile gate and friends`() {
        val ownerId = UUID.randomUUID()
        val viewerId = UUID.randomUUID()
        val follower = profile.domain.Relationship(
            isFollowing = true,
            isFollowedBy = false,
            isFriend = false,
            isBlocked = false,
            hasPendingRequest = false
        )
        val friend = follower.copy(isFollowedBy = true, isFriend = true)
        val privateProfile = PrivacySettings(
            userId = ownerId,
            isPrivate = true,
            fieldVisibility = FieldVisibility(
                bio = VisibilityAudience.PUBLIC,
                birthday = VisibilityAudience.FRIENDS,
                socialLinks = VisibilityAudience.FOLLOWERS
            )
        )

        assertTrue(ProfileVisibility.canView(ownerId, viewerId, follower, privateProfile, privateProfile.fieldVisibility.bio))
        assertFalse(ProfileVisibility.canView(ownerId, viewerId, follower, privateProfile, privateProfile.fieldVisibility.birthday))
        assertTrue(ProfileVisibility.canView(ownerId, viewerId, friend, privateProfile, privateProfile.fieldVisibility.birthday))
        assertTrue(ProfileVisibility.canView(ownerId, ownerId, follower, privateProfile, VisibilityAudience.PRIVATE))
    }

    @Test
    fun `birthday notification is idempotent and respects birthday preferences`() {
        val repo = FakeNotificationRepository()
        val useCases = NotificationUseCases(repo, FakeOutboxRepository())
        val recipientId = UUID.randomUUID()
        val birthdayUserId = UUID.randomUUID()

        val first = useCases.createBirthdayNotification(recipientId, birthdayUserId, "2026-07-04")
        val duplicate = useCases.createBirthdayNotification(recipientId, birthdayUserId, "2026-07-04")

        assertNotNull(first)
        assertNull(duplicate)
        assertEquals("account.birthday_today", repo.saved.single().type)

        repo.prefs[recipientId] = NotificationPrefs(userId = recipientId, inAppBirthdays = false)
        assertNull(useCases.createBirthdayNotification(recipientId, UUID.randomUUID(), "2026-07-04"))
    }

    @Test
    fun `activity fanout excludes blocked followers and uses recipient scoped idempotency`() {
        val social = FakeSocialRepository()
        val blocks = FakeBlockRepository()
        val privacy = FakePrivacyRepository()
        val socialUseCases = SocialUseCases(social, blocks, privacy)
        val notifications = FakeNotificationRepository()
        val notificationUseCases = NotificationUseCases(notifications, FakeOutboxRepository())
        val actorId = UUID.randomUUID()
        val followerA = UUID.randomUUID()
        val followerB = UUID.randomUUID()
        social.saveSubscription(Subscription(subscriberId = followerA, subscribedToId = actorId, status = SubscriptionStatus.ACCEPTED))
        social.saveSubscription(Subscription(subscriberId = followerB, subscribedToId = actorId, status = SubscriptionStatus.ACCEPTED))
        blocks.save(UserBlock(blockerId = actorId, blockedId = followerB))
        val event = UserActivityEvent(
            sourceEventId = "activity-2",
            actorId = actorId,
            activityType = UserActivityType.POST_PUBLISHED,
            entityType = "post",
            entityId = "post-2"
        )
        val processor = FakeOutboxProcessor(NotificationOutboxItem(UUID.randomUUID(), event, attempts = 0, createdAt = Instant.now()))
        val worker = NotificationOutboxWorker(processor, socialUseCases, blocks, notificationUseCases, SseManager())

        assertEquals(1, worker.processBatch())
        worker.processBatch()

        assertEquals(listOf(followerA), notifications.saved.map { it.recipientId })
        assertEquals("activity-2:recipient:$followerA", notifications.saved.single().sourceEventId)
    }

    @Test
    fun `grpc publish user activity returns accepted then duplicate`() {
        val fixtures = socialFixtures()
        val notificationUseCases = NotificationUseCases(FakeNotificationRepository(), FakeOutboxRepository())
        val service = SocialGrpcService(fixtures.socialUseCases, fixtures.blockRepo, fixtures.socialRepo, notificationUseCases)
        val actorId = UUID.randomUUID().toString()
        val request = PublishUserActivityRequest.newBuilder()
            .setSourceEventId("grpc-activity-1")
            .setActorId(actorId)
            .setActivityType(ProtoUserActivityType.POST_PUBLISHED)
            .setEntityType("post")
            .setEntityId("post-1")
            .build()

        val first = CapturingObserver<com.unlim.profile.grpc.v1.PublishUserActivityResponse>()
        val second = CapturingObserver<com.unlim.profile.grpc.v1.PublishUserActivityResponse>()
        service.publishUserActivity(request, first)
        service.publishUserActivity(request, second)

        assertTrue(first.value!!.accepted)
        assertFalse(first.value!!.duplicate)
        assertFalse(second.value!!.accepted)
        assertTrue(second.value!!.duplicate)
    }

    @Test
    fun `grpc publish user activity validates source event id`() {
        val fixtures = socialFixtures()
        val notificationUseCases = NotificationUseCases(FakeNotificationRepository(), FakeOutboxRepository())
        val service = SocialGrpcService(fixtures.socialUseCases, fixtures.blockRepo, fixtures.socialRepo, notificationUseCases)
        val observer = CapturingObserver<com.unlim.profile.grpc.v1.PublishUserActivityResponse>()
        val request = PublishUserActivityRequest.newBuilder()
            .setActorId(UUID.randomUUID().toString())
            .setActivityType(ProtoUserActivityType.POST_PUBLISHED)
            .build()

        service.publishUserActivity(request, observer)

        val error = observer.error as StatusRuntimeException
        assertEquals(Status.Code.INVALID_ARGUMENT, error.status.code)
    }

    private fun socialFixtures(): SocialFixtures {
        val socialRepo = FakeSocialRepository()
        val blockRepo = FakeBlockRepository()
        val privacyRepo = FakePrivacyRepository()
        return SocialFixtures(socialRepo, blockRepo, privacyRepo, SocialUseCases(socialRepo, blockRepo, privacyRepo))
    }
}

private data class SocialFixtures(
    val socialRepo: FakeSocialRepository,
    val blockRepo: FakeBlockRepository,
    val privacyRepo: FakePrivacyRepository,
    val socialUseCases: SocialUseCases
)

private class FakeSocialRepository : SocialRepository {
    private val subscriptions = linkedMapOf<Pair<UUID, UUID>, Subscription>()

    override fun saveSubscription(sub: Subscription) {
        subscriptions[sub.subscriberId to sub.subscribedToId] = sub
    }

    override fun updateSubscription(sub: Subscription) {
        subscriptions[sub.subscriberId to sub.subscribedToId] = sub
    }

    override fun deleteSubscription(id: UUID) {
        subscriptions.entries.removeIf { it.value.id == id }
    }

    override fun findSubscription(subscriberId: UUID, subscribedToId: UUID): Subscription? =
        subscriptions[subscriberId to subscribedToId]

    override fun findBySubscriber(subscriberId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        val all = subscriptions.values.filter { it.subscriberId == subscriberId && it.status == SubscriptionStatus.ACCEPTED }
        return all.drop(offset).take(limit) to all.size
    }

    override fun findBySubscribedTo(subscribedToId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        val all = subscriptions.values.filter { it.subscribedToId == subscribedToId && it.status == SubscriptionStatus.ACCEPTED }
        return all.drop(offset).take(limit) to all.size
    }

    override fun findPendingBySubscribedTo(subscribedToId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int> {
        val all = subscriptions.values.filter { it.subscribedToId == subscribedToId && it.status == SubscriptionStatus.PENDING }
        return all.drop(offset).take(limit) to all.size
    }

    override fun countFollowers(userId: UUID): Long =
        subscriptions.values.count { it.subscribedToId == userId && it.status == SubscriptionStatus.ACCEPTED }.toLong()

    override fun countFollowing(userId: UUID): Long =
        subscriptions.values.count { it.subscriberId == userId && it.status == SubscriptionStatus.ACCEPTED }.toLong()
}

private class FakeBlockRepository : BlockRepository {
    private val blocks = mutableSetOf<Pair<UUID, UUID>>()

    override fun save(block: UserBlock) {
        blocks.add(block.blockerId to block.blockedId)
    }

    override fun delete(blockerId: UUID, blockedId: UUID) {
        blocks.remove(blockerId to blockedId)
    }

    override fun isBlockedEither(a: UUID, b: UUID): Boolean =
        (a to b) in blocks || (b to a) in blocks

    override fun findByBlocker(blockerId: UUID): List<UserBlock> =
        blocks.filter { it.first == blockerId }.map { UserBlock(blockerId = it.first, blockedId = it.second) }
}

private class FakePrivacyRepository : PrivacyRepository {
    private val settings = mutableMapOf<UUID, PrivacySettings>()

    override fun get(userId: UUID): PrivacySettings = settings[userId] ?: PrivacySettings(userId = userId)

    override fun save(settings: PrivacySettings) {
        this.settings[settings.userId] = settings
    }
}

private class FakeNotificationRepository : NotificationRepository {
    val saved = mutableListOf<Notification>()
    val prefs = mutableMapOf<UUID, NotificationPrefs>()

    override fun save(n: Notification) {
        if (n.sourceEventId == null || saved.none { it.sourceEventId == n.sourceEventId }) saved.add(n)
    }

    override fun existsBySourceEventId(eventId: String): Boolean = saved.any { it.sourceEventId == eventId }

    override fun findByRecipient(recipientId: UUID, offset: Int, limit: Int): Pair<List<Notification>, Int> {
        val all = saved.filter { it.recipientId == recipientId }
        return all.drop(offset).take(limit) to all.size
    }

    override fun countUnread(recipientId: UUID): Int = saved.count { it.recipientId == recipientId && !it.isRead }

    override fun markRead(id: UUID) = Unit

    override fun markAllRead(recipientId: UUID) = Unit

    override fun getPrefs(userId: UUID): NotificationPrefs = prefs[userId] ?: NotificationPrefs(userId)

    override fun savePrefs(prefs: NotificationPrefs) {
        this.prefs[prefs.userId] = prefs
    }

    override fun registerCatalog(catalog: NotificationServiceCatalog) = Unit

    override fun activateServiceForUser(userId: UUID, serviceKey: String) = Unit

    override fun notificationTypeExists(serviceKey: String, typeKey: String): Boolean = true

    override fun notificationTypeEnabled(userId: UUID, serviceKey: String, typeKey: String, owner: OwnerRef?): Boolean {
        val prefs = getPrefs(userId)
        return when ("$serviceKey.$typeKey") {
            "content.post_published" -> prefs.inAppPublications
            "content.story_published" -> prefs.inAppNewStories
            "content.author_mention" -> prefs.inAppAuthorMentions
            "content.post_comment" -> prefs.inAppPostComments
            "account.subscription_request", "account.subscription_accepted", "account.organization_invitation" -> prefs.inAppSubscriptions
            "account.birthday_today" -> prefs.inAppBirthdays
            else -> true
        }
    }

    override fun getLocalizedSettings(userId: UUID, locale: String, owner: OwnerRef?): List<LocalizedNotificationServiceSettings> = emptyList()

    override fun savePreference(userId: UUID, serviceKey: String, typeKey: String, enabled: Boolean) = Unit

    override fun saveOwnerPreference(userId: UUID, owner: OwnerRef, serviceKey: String, typeKey: String, enabled: Boolean) = Unit
}

private class FakeOutboxRepository : NotificationOutboxRepository {
    private val sourceEventIds = mutableSetOf<String>()

    override fun enqueue(event: UserActivityEvent): Boolean = sourceEventIds.add(event.sourceEventId)
}

private class FakeOutboxProcessor(private val item: NotificationOutboxItem) : NotificationOutboxProcessor {
    override fun processPending(limit: Int, handler: (NotificationOutboxItem) -> Unit): Int {
        handler(item)
        return 1
    }
}

private class CapturingObserver<T> : StreamObserver<T> {
    var value: T? = null
    var error: Throwable? = null
    var completed: Boolean = false

    override fun onNext(value: T) {
        this.value = value
    }

    override fun onError(t: Throwable) {
        error = t
    }

    override fun onCompleted() {
        completed = true
    }
}
