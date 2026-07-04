package profile.api.grpc

import com.unlim.profile.grpc.v1.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import profile.usecases.*
import profile.domain.PublishActivityStatus
import profile.domain.UserActivityType as DomainUserActivityType
import java.util.UUID

class SocialGrpcService(
    private val socialUseCases: SocialUseCases,
    private val blockRepo: BlockRepository,
    private val socialRepo: SocialRepository,
    private val notificationUseCases: NotificationUseCases,
) : ProfileServiceGrpc.ProfileServiceImplBase() {

    override fun getRelationship(request: RelRequest, responseObserver: StreamObserver<RelResponse>) {
        try {
            val userId = UUID.fromString(request.userId)
            val targetId = UUID.fromString(request.targetId)
            val rel = socialUseCases.getRelationship(userId, targetId)
            responseObserver.onNext(RelResponse.newBuilder()
                .setIsFollowing(rel.isFollowing).setIsFollowedBy(rel.isFollowedBy)
                .setIsFriend(rel.isFriend).setIsBlocked(rel.isBlocked)
                .setHasPendingRequest(rel.hasPendingRequest).build())
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun isBlocked(request: BlockRequest, responseObserver: StreamObserver<BlockResponse>) {
        try {
            val userA = UUID.fromString(request.userA)
            val userB = UUID.fromString(request.userB)
            val blocked = blockRepo.isBlockedEither(userA, userB)
            responseObserver.onNext(BlockResponse.newBuilder().setBlocked(blocked).build())
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun getFollowingIds(request: FollowingRequest, responseObserver: StreamObserver<FollowingResponse>) {
        try {
            val userId = UUID.fromString(request.userId)
            val (items, _) = socialUseCases.getFollowing(userId, 1, 10000)
            val ids = items.map { it.subscribedToId.toString() }
            responseObserver.onNext(FollowingResponse.newBuilder().addAllUserIds(ids).build())
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun createNotification(request: CreateNotifRequest, responseObserver: StreamObserver<CreateNotifResponse>) {
        try {
            val notif = notificationUseCases.createFromEvent(
                eventId = request.sourceEventId,
                recipientId = UUID.fromString(request.recipientId),
                type = request.type,
                title = request.title,
                body = request.body,
                actorId = if (request.actorId.isNotBlank()) UUID.fromString(request.actorId) else null,
                entityType = request.entityType.ifBlank { null },
                entityId = request.entityId.ifBlank { null },
                metadataJson = request.metadataJson.ifBlank { "{}" },
            )
            responseObserver.onNext(CreateNotifResponse.newBuilder()
                .setNotificationId(notif?.id?.toString() ?: "").build())
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun publishUserActivity(request: PublishUserActivityRequest, responseObserver: StreamObserver<PublishUserActivityResponse>) {
        try {
            if (request.sourceEventId.isBlank()) {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("source_event_id is required").asRuntimeException())
                return
            }
            if (request.actorId.isBlank()) {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("actor_id is required").asRuntimeException())
                return
            }
            val activityType = when (request.activityType) {
                UserActivityType.POST_PUBLISHED -> DomainUserActivityType.POST_PUBLISHED
                UserActivityType.STORY_PUBLISHED -> DomainUserActivityType.STORY_PUBLISHED
                UserActivityType.AUTHOR_MENTION -> DomainUserActivityType.AUTHOR_MENTION
                UserActivityType.POST_COMMENT -> DomainUserActivityType.POST_COMMENT
                else -> {
                    responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("activity_type is required").asRuntimeException())
                    return
                }
            }
            val result = notificationUseCases.publishUserActivity(
                sourceEventId = request.sourceEventId,
                actorId = UUID.fromString(request.actorId),
                activityType = activityType,
                entityType = request.entityType.ifBlank { null },
                entityId = request.entityId.ifBlank { null },
                metadataJson = request.metadataJson.ifBlank { "{}" }
            )
            responseObserver.onNext(PublishUserActivityResponse.newBuilder()
                .setSourceEventId(result.sourceEventId)
                .setAccepted(result.status == PublishActivityStatus.ACCEPTED)
                .setDuplicate(result.status == PublishActivityStatus.DUPLICATE)
                .build())
            responseObserver.onCompleted()
        } catch (e: IllegalArgumentException) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException())
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun getFollowersCount(request: UserIdRequest, responseObserver: StreamObserver<CountResponse>) {
        try {
            val userId = UUID.fromString(request.userId)
            val count = socialRepo.countFollowers(userId)
            responseObserver.onNext(CountResponse.newBuilder().setCount(count.toLong()).build())
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun getFollowingCount(request: UserIdRequest, responseObserver: StreamObserver<CountResponse>) {
        try {
            val userId = UUID.fromString(request.userId)
            val count = socialRepo.countFollowing(userId)
            responseObserver.onNext(CountResponse.newBuilder().setCount(count.toLong()).build())
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }
}
