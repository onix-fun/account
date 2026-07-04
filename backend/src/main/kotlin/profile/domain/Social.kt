package profile.domain

import java.time.Instant
import java.util.UUID
import com.fasterxml.uuid.Generators

data class Subscription(
    val id: UUID = Generators.timeBasedEpochGenerator().generate(),
    val subscriberId: UUID,
    val subscribedToId: UUID,
    val status: SubscriptionStatus = SubscriptionStatus.PENDING,
    val isCloseFriend: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class SubscriptionStatus { PENDING, ACCEPTED }

data class UserBlock(
    val id: UUID = Generators.timeBasedEpochGenerator().generate(),
    val blockerId: UUID,
    val blockedId: UUID,
    val createdAt: Instant = Instant.now()
)

data class PrivacySettings(
    val userId: UUID,
    val isPrivate: Boolean = false,
    val updatedAt: Instant = Instant.now()
)

data class Relationship(
    val isFollowing: Boolean,
    val isFollowedBy: Boolean,
    val isFriend: Boolean,
    val isBlocked: Boolean,
    val hasPendingRequest: Boolean
)

data class Profile(
    val userId: UUID,
    val username: String,
    val firstName: String?,
    val lastName: String?,
    val bio: String?,
    val avatarUrl: String?,
    val followersCount: Long,
    val followingCount: Long,
    val isPrivate: Boolean,
)
