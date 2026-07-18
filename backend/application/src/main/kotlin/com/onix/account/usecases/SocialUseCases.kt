package com.onix.account.usecases

import com.onix.account.domain.*
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
    fun delete(blocker: OwnerRef, blocked: OwnerRef) = delete(blocker.id, blocked.id)
    fun isBlockedEither(a: UUID, b: UUID): Boolean
    fun isBlockedEither(a: OwnerRef, b: OwnerRef): Boolean =
        if (a.type == OwnerType.USER && b.type == OwnerType.USER) isBlockedEither(a.id, b.id) else false
    fun findByBlocker(blockerId: UUID): List<UserBlock>
    fun findByBlocker(blocker: OwnerRef): List<UserBlock> =
        if (blocker.type == OwnerType.USER) findByBlocker(blocker.id) else emptyList()
}

interface PrivacyRepository {
    fun get(userId: UUID): PrivacySettings
    fun get(owner: OwnerRef): PrivacySettings =
        if (owner.type == OwnerType.USER) get(owner.id) else PrivacySettings(ownerType = owner.type, userId = owner.id)
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
        require(!blockRepo.isBlockedEither(subscriber, target)) { "Blocked" }

        val existing = socialRepo.findSubscription(subscriber, target)
        if (existing != null) return existing

        val status = if (privacyRepo.get(target).isPrivate) {
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
        return blockOwner(OwnerRef.user(blockerId), OwnerRef.user(blockedId))
    }

    fun unblockUser(blockerId: UUID, blockedId: UUID): Boolean {
        unblockOwner(OwnerRef.user(blockerId), OwnerRef.user(blockedId))
        return true
    }

    fun blockOwner(blocker: OwnerRef, blocked: OwnerRef): Boolean {
        require(blocker != blocked) { "Cannot block yourself" }
        blockRepo.save(UserBlock(
            blockerType = blocker.type,
            blockerId = blocker.id,
            blockedType = blocked.type,
            blockedId = blocked.id
        ))
        socialRepo.findSubscription(blocker, blocked)?.let { socialRepo.deleteSubscription(it.id) }
        socialRepo.findSubscription(blocked, blocker)?.let { socialRepo.deleteSubscription(it.id) }
        return true
    }

    fun unblockOwner(blocker: OwnerRef, blocked: OwnerRef): Boolean {
        blockRepo.delete(blocker, blocked)
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
        val isBlocked = blockRepo.isBlockedEither(current, target)
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

    fun getBlockedUsers(owner: OwnerRef): List<UserBlock> = blockRepo.findByBlocker(owner)

    fun getPrivacySettings(userId: UUID): PrivacySettings = privacyRepo.get(userId)

    fun getPrivacySettings(owner: OwnerRef): PrivacySettings = privacyRepo.get(owner)

    fun updatePrivacySettings(userId: UUID, isPrivate: Boolean, fieldVisibility: FieldVisibility? = null): PrivacySettings {
        return updatePrivacySettings(OwnerRef.user(userId), isPrivate, fieldVisibility)
    }

    fun updatePrivacySettings(owner: OwnerRef, isPrivate: Boolean, fieldVisibility: FieldVisibility? = null): PrivacySettings {
        val current = privacyRepo.get(owner)
        val settings = PrivacySettings(
            ownerType = owner.type,
            userId = owner.id,
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
