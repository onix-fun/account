package profile.usecases

import profile.domain.*
import java.time.Instant
import java.util.UUID

interface SocialRepository {
    fun saveSubscription(sub: Subscription)
    fun updateSubscription(sub: Subscription)
    fun deleteSubscription(id: UUID)
    fun findSubscription(subscriberId: UUID, subscribedToId: UUID): Subscription?
    fun findSubscription(subscriber: OwnerRef, subscribedTo: OwnerRef): Subscription? =
        if (subscriber.type == OwnerType.USER && subscribedTo.type == OwnerType.USER) findSubscription(subscriber.id, subscribedTo.id) else null
    fun findBySubscriber(subscriberId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int>
    fun findBySubscriber(subscriber: OwnerRef, offset: Int, limit: Int): Pair<List<Subscription>, Int> =
        if (subscriber.type == OwnerType.USER) findBySubscriber(subscriber.id, offset, limit) else emptyList<Subscription>() to 0
    fun findBySubscribedTo(subscribedToId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int>
    fun findBySubscribedTo(subscribedTo: OwnerRef, offset: Int, limit: Int): Pair<List<Subscription>, Int> =
        if (subscribedTo.type == OwnerType.USER) findBySubscribedTo(subscribedTo.id, offset, limit) else emptyList<Subscription>() to 0
    fun findPendingBySubscribedTo(subscribedToId: UUID, offset: Int, limit: Int): Pair<List<Subscription>, Int>
    fun countFollowers(userId: UUID): Long
    fun countFollowers(owner: OwnerRef): Long =
        if (owner.type == OwnerType.USER) countFollowers(owner.id) else 0
    fun countFollowing(userId: UUID): Long
    fun countFollowing(owner: OwnerRef): Long =
        if (owner.type == OwnerType.USER) countFollowing(owner.id) else 0
}

interface BlockRepository {
    fun save(block: UserBlock)
    fun delete(blockerId: UUID, blockedId: UUID)
    fun isBlockedEither(a: UUID, b: UUID): Boolean
    fun findByBlocker(blockerId: UUID): List<UserBlock>
}

interface PrivacyRepository {
    fun get(userId: UUID): PrivacySettings
    fun save(settings: PrivacySettings)
}

class SocialUseCases(
    private val socialRepo: SocialRepository,
    private val blockRepo: BlockRepository,
    private val privacyRepo: PrivacyRepository
) {
    fun subscribe(subscriberId: UUID, targetId: UUID): Subscription {
        return subscribe(OwnerRef.user(subscriberId), OwnerRef.user(targetId))
    }

    fun subscribe(subscriber: OwnerRef, target: OwnerRef): Subscription {
        require(subscriber != target) { "Cannot subscribe to yourself" }
        if (subscriber.type == OwnerType.USER && target.type == OwnerType.USER) {
            require(!blockRepo.isBlockedEither(subscriber.id, target.id)) { "Blocked" }
        }

        val existing = socialRepo.findSubscription(subscriber, target)
        if (existing != null) return existing

        val status = if (target.type == OwnerType.USER && privacyRepo.get(target.id).isPrivate) {
            SubscriptionStatus.PENDING
        } else {
            SubscriptionStatus.ACCEPTED
        }

        val sub = Subscription(
            subscriberType = subscriber.type,
            subscriberId = subscriber.id,
            subscribedToType = target.type,
            subscribedToId = target.id,
            status = status,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        socialRepo.saveSubscription(sub)
        return sub
    }

    fun subscribeLegacy(subscriberId: UUID, targetId: UUID): Subscription {
        require(subscriberId != targetId) { "Cannot subscribe to yourself" }
        require(!blockRepo.isBlockedEither(subscriberId, targetId)) { "Blocked" }

        val existing = socialRepo.findSubscription(subscriberId, targetId)
        if (existing != null) return existing

        val targetPrivacy = privacyRepo.get(targetId)
        val status = if (targetPrivacy.isPrivate) SubscriptionStatus.PENDING else SubscriptionStatus.ACCEPTED

        val sub = Subscription(
            subscriberId = subscriberId, subscribedToId = targetId, status = status,
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        socialRepo.saveSubscription(sub)
        return sub
    }

    fun acceptSubscription(subscriberId: UUID, targetId: UUID): Subscription {
        val sub = socialRepo.findSubscription(subscriberId, targetId)
            ?: throw IllegalArgumentException("Subscription not found")
        require(sub.subscribedToId == targetId) { "Not authorized" }

        val updated = sub.copy(status = SubscriptionStatus.ACCEPTED, updatedAt = Instant.now())
        socialRepo.updateSubscription(updated)
        return updated
    }

    fun removeSubscription(subscriberId: UUID, targetId: UUID): Boolean {
        val sub = socialRepo.findSubscription(subscriberId, targetId) ?: return false
        socialRepo.deleteSubscription(sub.id)
        return true
    }

    fun removeSubscription(subscriber: OwnerRef, target: OwnerRef): Boolean {
        val sub = socialRepo.findSubscription(subscriber, target) ?: return false
        socialRepo.deleteSubscription(sub.id)
        return true
    }

    fun blockUser(blockerId: UUID, blockedId: UUID): Boolean {
        require(blockerId != blockedId) { "Cannot block yourself" }
        blockRepo.save(UserBlock(blockerId = blockerId, blockedId = blockedId))
        socialRepo.findSubscription(blockerId, blockedId)?.let { socialRepo.deleteSubscription(it.id) }
        socialRepo.findSubscription(blockedId, blockerId)?.let { socialRepo.deleteSubscription(it.id) }
        return true
    }

    fun unblockUser(blockerId: UUID, blockedId: UUID): Boolean {
        blockRepo.delete(blockerId, blockedId)
        return true
    }

    fun getRelationship(currentUserId: UUID?, targetId: UUID): Relationship {
        if (currentUserId == null) return Relationship(false, false, false, false, false)
        val isBlocked = blockRepo.isBlockedEither(currentUserId, targetId)
        val forward = socialRepo.findSubscription(currentUserId, targetId)
        val reverse = socialRepo.findSubscription(targetId, currentUserId)
        return Relationship(
            isFollowing = forward?.status == SubscriptionStatus.ACCEPTED,
            isFollowedBy = reverse?.status == SubscriptionStatus.ACCEPTED,
            isFriend = forward?.status == SubscriptionStatus.ACCEPTED && reverse?.status == SubscriptionStatus.ACCEPTED,
            isBlocked = isBlocked,
            hasPendingRequest = forward?.status == SubscriptionStatus.PENDING
        )
    }

    fun getRelationship(current: OwnerRef?, target: OwnerRef): Relationship {
        if (current == null) return Relationship(false, false, false, false, false)
        val isBlocked = if (current.type == OwnerType.USER && target.type == OwnerType.USER) {
            blockRepo.isBlockedEither(current.id, target.id)
        } else {
            false
        }
        val forward = socialRepo.findSubscription(current, target)
        val reverse = socialRepo.findSubscription(target, current)
        return Relationship(
            isFollowing = forward?.status == SubscriptionStatus.ACCEPTED,
            isFollowedBy = reverse?.status == SubscriptionStatus.ACCEPTED,
            isFriend = forward?.status == SubscriptionStatus.ACCEPTED && reverse?.status == SubscriptionStatus.ACCEPTED,
            isBlocked = isBlocked,
            hasPendingRequest = forward?.status == SubscriptionStatus.PENDING
        )
    }

    fun getFollowers(userId: UUID, page: Int, limit: Int): Pair<List<Subscription>, Int> =
        socialRepo.findBySubscribedTo(userId, (page - 1) * limit, limit)

    fun getFollowing(userId: UUID, page: Int, limit: Int): Pair<List<Subscription>, Int> =
        socialRepo.findBySubscriber(userId, (page - 1) * limit, limit)

    fun getFollowers(owner: OwnerRef, page: Int, limit: Int): Pair<List<Subscription>, Int> =
        socialRepo.findBySubscribedTo(owner, (page - 1) * limit, limit)

    fun getFollowing(owner: OwnerRef, page: Int, limit: Int): Pair<List<Subscription>, Int> =
        socialRepo.findBySubscriber(owner, (page - 1) * limit, limit)

    fun getPendingRequests(userId: UUID, page: Int, limit: Int): Pair<List<Subscription>, Int> {
        return socialRepo.findPendingBySubscribedTo(userId, (page - 1) * limit, limit)
    }

    fun getBlockedUsers(blockerId: UUID): List<UserBlock> = blockRepo.findByBlocker(blockerId)

    fun getPrivacySettings(userId: UUID): PrivacySettings = privacyRepo.get(userId)

    fun updatePrivacySettings(userId: UUID, isPrivate: Boolean, fieldVisibility: FieldVisibility? = null): PrivacySettings {
        val current = privacyRepo.get(userId)
        val settings = PrivacySettings(
            userId = userId,
            isPrivate = isPrivate,
            fieldVisibility = fieldVisibility ?: current.fieldVisibility,
            updatedAt = Instant.now()
        )
        privacyRepo.save(settings)
        return settings
    }

    fun getCloseFriends(userId: UUID): List<Subscription> {
        val (items, _) = socialRepo.findBySubscriber(userId, 0, 1000)
        return items.filter { it.isCloseFriend && it.status == SubscriptionStatus.ACCEPTED }
    }

    fun markCloseFriend(subscriberId: UUID, targetId: UUID): Boolean {
        val sub = socialRepo.findSubscription(subscriberId, targetId)
            ?: throw IllegalArgumentException("Not subscribed")
        require(sub.status == SubscriptionStatus.ACCEPTED) { "Subscription is not accepted" }
        val updated = sub.copy(isCloseFriend = true, updatedAt = Instant.now())
        socialRepo.updateSubscription(updated)
        return true
    }

    fun unmarkCloseFriend(subscriberId: UUID, targetId: UUID): Boolean {
        val sub = socialRepo.findSubscription(subscriberId, targetId) ?: return false
        val updated = sub.copy(isCloseFriend = false, updatedAt = Instant.now())
        socialRepo.updateSubscription(updated)
        return true
    }
}
