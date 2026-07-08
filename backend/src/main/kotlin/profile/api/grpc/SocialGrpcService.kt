package profile.api.grpc

import com.unlim.profile.grpc.v1.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import profile.domain.NotificationServiceCatalog
import profile.domain.NotificationTypeCatalog
import profile.domain.ProfileVisibility
import profile.domain.PublishActivityStatus
import profile.domain.SocialLink as DomainSocialLink
import profile.domain.UserActivityType as DomainUserActivityType
import profile.infrastructure.PrivacyRepo
import profile.infrastructure.SseManager
import profile.infrastructure.db.User
import profile.search.SearchService
import profile.usecases.*
import profile.users.UserService
import profile.users.toBirthdayParts
import java.util.UUID

class SocialGrpcService(
    private val userService: UserService?,
    private val searchService: SearchService?,
    private val socialUseCases: SocialUseCases,
    private val blockRepo: BlockRepository,
    private val socialRepo: SocialRepository,
    private val privacyRepo: PrivacyRepo?,
    private val notificationUseCases: NotificationUseCases,
    private val sseManager: SseManager,
    private val auth: GrpcPrincipalResolver?,
    private val requireUserToken: Boolean = true,
) : ProfileServiceGrpc.ProfileServiceImplBase() {

    constructor(
        socialUseCases: SocialUseCases,
        blockRepo: BlockRepository,
        socialRepo: SocialRepository,
        notificationUseCases: NotificationUseCases
    ) : this(null, null, socialUseCases, blockRepo, socialRepo, null, notificationUseCases, SseManager(), null, false)

    override fun getCurrentUser(request: CurrentUserRequest, responseObserver: StreamObserver<AccountUser>) =
        unary(responseObserver) {
            val userId = requireUserId()
            val user = userService().getProfile(userId) ?: throw Status.NOT_FOUND.asRuntimeException()
            user.toGrpcUser()
        }

    override fun getUserById(request: GetUserByIdRequest, responseObserver: StreamObserver<AccountUser>) =
        unary(responseObserver) {
            requireUserId()
            val user = userService().getProfile(request.userId) ?: throw Status.NOT_FOUND.asRuntimeException()
            user.toGrpcUser()
        }

    override fun getProfileByUsername(request: GetProfileByUsernameRequest, responseObserver: StreamObserver<AccountProfile>) =
        unary(responseObserver) {
            val viewerId = UUID.fromString(requireUserId())
            val user = userService().getProfileByUsername(request.username) ?: throw Status.NOT_FOUND.asRuntimeException()
            val ownerId = UUID.fromString(user.id)
            val privacy = privacyRepo().get(ownerId)
            val relationship = socialUseCases.getRelationship(viewerId, ownerId)
            val filtered = user.copy(
                bio = user.bio.takeIf { ProfileVisibility.canView(ownerId, viewerId, relationship, privacy, privacy.fieldVisibility.bio) },
                birthDate = user.birthDate.takeIf { ProfileVisibility.canView(ownerId, viewerId, relationship, privacy, privacy.fieldVisibility.birthday) },
                socialLinks = user.socialLinks.takeIf { ProfileVisibility.canView(ownerId, viewerId, relationship, privacy, privacy.fieldVisibility.socialLinks) }.orEmpty()
            )
            AccountProfile.newBuilder()
                .setUser(filtered.toGrpcUser())
                .setFollowersCount(socialRepo.countFollowers(ownerId).toLong())
                .setFollowingCount(socialRepo.countFollowing(ownerId).toLong())
                .setIsPrivate(privacy.isPrivate)
                .setRelationship(relationship.toGrpc())
                .build()
        }

    override fun searchUsers(request: SearchUsersRequest, responseObserver: StreamObserver<UserListResponse>) =
        unary(responseObserver) {
            requireUserId()
            UserListResponse.newBuilder()
                .addAllUsers(searchService().searchByUsernamePrefix(request.query, request.limit.coerceIn(1, 50)).map {
                    AccountUser.newBuilder()
                        .setId(it.id)
                        .setUsername(it.username)
                        .setFirstName(it.firstName.orEmpty())
                        .setLastName(it.lastName.orEmpty())
                        .setAvatarUrl(it.avatarUrl.orEmpty())
                        .setBio(it.bio.orEmpty())
                        .addAllSocialLinks(it.socialLinks.map { link -> link.toGrpc() })
                        .also { builder ->
                            it.birthday?.let { birthday ->
                                builder.setBirthday(BirthdayParts.newBuilder().setDay(birthday.day).setMonth(birthday.month).build())
                            }
                        }
                        .build()
                })
                .build()
        }

    override fun getRelationship(request: RelRequest, responseObserver: StreamObserver<RelResponse>) =
        unary(responseObserver) {
            requireUserId()
            socialUseCases.getRelationship(UUID.fromString(request.userId), UUID.fromString(request.targetId)).toGrpc()
        }

    override fun follow(request: FollowRequest, responseObserver: StreamObserver<RelResponse>) =
        unary(responseObserver) {
            val userId = UUID.fromString(requireUserId())
            val targetId = UUID.fromString(request.targetId)
            socialUseCases.subscribe(userId, targetId)
            socialUseCases.getRelationship(userId, targetId).toGrpc()
        }

    override fun unfollow(request: FollowRequest, responseObserver: StreamObserver<Empty>) =
        unary(responseObserver) {
            val userId = UUID.fromString(requireUserId())
            socialUseCases.removeSubscription(userId, UUID.fromString(request.targetId))
            Empty.getDefaultInstance()
        }

    override fun listFollowers(request: UserPageRequest, responseObserver: StreamObserver<UserPageResponse>) =
        unary(responseObserver) {
            val viewerId = UUID.fromString(requireUserId())
            val targetId = UUID.fromString(request.userId)
            val (items, total) = socialUseCases.getFollowers(targetId, request.page.coerceAtLeast(1), request.limit.coerceIn(1, 100))
            UserPageResponse.newBuilder()
                .setTotalCount(total)
                .addAllItems(items.mapNotNull { sub ->
                    userService().getProfile(sub.subscriberId.toString())?.toRelatedGrpc(socialUseCases.getRelationship(viewerId, sub.subscriberId))
                })
                .build()
        }

    override fun listFollowing(request: UserPageRequest, responseObserver: StreamObserver<UserPageResponse>) =
        unary(responseObserver) {
            val viewerId = UUID.fromString(requireUserId())
            val targetId = UUID.fromString(request.userId)
            val (items, total) = socialUseCases.getFollowing(targetId, request.page.coerceAtLeast(1), request.limit.coerceIn(1, 100))
            UserPageResponse.newBuilder()
                .setTotalCount(total)
                .addAllItems(items.mapNotNull { sub ->
                    userService().getProfile(sub.subscribedToId.toString())?.toRelatedGrpc(socialUseCases.getRelationship(viewerId, sub.subscribedToId))
                })
                .build()
        }

    override fun getVisibility(request: VisibilityRequest, responseObserver: StreamObserver<VisibilityResponse>) =
        unary(responseObserver) {
            val tokenUserId = UUID.fromString(requireUserId())
            val viewerId = request.viewerId.takeIf(String::isNotBlank)?.let(UUID::fromString) ?: tokenUserId
            if (viewerId != tokenUserId) throw Status.PERMISSION_DENIED.withDescription("viewer_id must match access token").asRuntimeException()
            val ownerId = UUID.fromString(request.ownerId)
            val relationship = socialUseCases.getRelationship(viewerId, ownerId)
            val privacy = socialUseCases.getPrivacySettings(ownerId)
            val isCloseFriend = socialUseCases.getCloseFriends(ownerId).any { it.subscribedToId == viewerId }
            VisibilityResponse.newBuilder()
                .setOwnerId(ownerId.toString())
                .setViewerId(viewerId.toString())
                .setIsPrivate(privacy.isPrivate)
                .setRelationship(relationship.toGrpc())
                .setIsBlocked(relationship.isBlocked)
                .setIsCloseFriend(isCloseFriend)
                .build()
        }

    override fun getSocialGraph(request: UserIdRequest, responseObserver: StreamObserver<SocialGraphResponse>) =
        unary(responseObserver) {
            val tokenUserId = UUID.fromString(requireUserId())
            val viewerId = UUID.fromString(request.userId)
            if (viewerId != tokenUserId) throw Status.PERMISSION_DENIED.withDescription("user_id must match access token").asRuntimeException()
            val (following, _) = socialUseCases.getFollowing(viewerId, page = 1, limit = 1000)
            val (followers, _) = socialUseCases.getFollowers(viewerId, page = 1, limit = 1000)
            val followingIds = following.map { it.subscribedToId.toString() }
            val followerIds = followers.map { it.subscriberId.toString() }.toSet()
            SocialGraphResponse.newBuilder()
                .addAllFollowingIds(followingIds)
                .addAllFriendIds(followingIds.filter { it in followerIds })
                .addAllBlockedIds(socialUseCases.getBlockedUsers(viewerId).map { it.blockedId.toString() })
                .build()
        }

    override fun isBlocked(request: BlockRequest, responseObserver: StreamObserver<BlockResponse>) =
        unary(responseObserver) {
            requireUserId()
            BlockResponse.newBuilder()
                .setBlocked(blockRepo.isBlockedEither(UUID.fromString(request.userA), UUID.fromString(request.userB)))
                .build()
        }

    override fun getFollowingIds(request: FollowingRequest, responseObserver: StreamObserver<FollowingResponse>) =
        unary(responseObserver) {
            requireUserId()
            val (items, _) = socialUseCases.getFollowing(UUID.fromString(request.userId), 1, 10000)
            FollowingResponse.newBuilder().addAllUserIds(items.map { it.subscribedToId.toString() }).build()
        }

    override fun createNotification(request: CreateNotifRequest, responseObserver: StreamObserver<CreateNotifResponse>) =
        unary(responseObserver) {
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
            CreateNotifResponse.newBuilder().setNotificationId(notif?.id?.toString() ?: "").build()
        }

    override fun sendNotificationToUser(request: SendNotificationToUserRequest, responseObserver: StreamObserver<CreateNotifResponse>) =
        unary(responseObserver) {
            val notif = notificationUseCases.sendToUser(
                sourceEventId = request.sourceEventId,
                recipientId = UUID.fromString(request.recipientId),
                serviceKey = request.serviceKey,
                typeKey = request.typeKey,
                title = request.title.toDomain(),
                body = request.body.toDomain(),
                actorId = if (request.actorId.isNotBlank()) UUID.fromString(request.actorId) else null,
                entityType = request.entityType.ifBlank { null },
                entityId = request.entityId.ifBlank { null },
                metadataJson = request.metadataJson.ifBlank { "{}" },
            )
            if (notif != null) sseManager.push(request.recipientId, notif)
            CreateNotifResponse.newBuilder().setNotificationId(notif?.id?.toString() ?: "").build()
        }

    override fun registerNotificationCatalog(request: RegisterNotificationCatalogRequest, responseObserver: StreamObserver<Empty>) =
        unary(responseObserver) {
            notificationUseCases.registerCatalog(NotificationServiceCatalog(
                serviceKey = request.serviceKey,
                name = request.name.toDomain(),
                description = request.description.toDomain(),
                icon = request.icon,
                displayOrder = request.displayOrder,
                types = request.typesList.map {
                    NotificationTypeCatalog(
                        serviceKey = request.serviceKey,
                        typeKey = it.typeKey,
                        name = it.name.toDomain(),
                        description = it.description.toDomain(),
                        icon = it.icon,
                        defaultEnabled = it.defaultEnabled,
                        displayOrder = it.displayOrder
                    )
                }
            ))
            Empty.getDefaultInstance()
        }

    override fun activateNotificationServiceForUser(request: ActivateNotificationServiceForUserRequest, responseObserver: StreamObserver<Empty>) =
        unary(responseObserver) {
            val tokenUserId = requireUserId(request.userId.takeIf(String::isNotBlank))
            if (request.userId.isNotBlank() && request.userId != tokenUserId) {
                throw Status.PERMISSION_DENIED.withDescription("user_id must match access token").asRuntimeException()
            }
            notificationUseCases.activateServiceForUser(UUID.fromString(tokenUserId), request.serviceKey)
            Empty.getDefaultInstance()
        }

    override fun publishUserActivity(request: PublishUserActivityRequest, responseObserver: StreamObserver<PublishUserActivityResponse>) =
        unary(responseObserver) {
            val tokenUserId = requireUserId(request.actorId)
            if (request.actorId != tokenUserId) {
                throw Status.PERMISSION_DENIED.withDescription("actor_id must match access token").asRuntimeException()
            }
            val activityType = when (request.activityType) {
                UserActivityType.POST_PUBLISHED -> DomainUserActivityType.POST_PUBLISHED
                UserActivityType.STORY_PUBLISHED -> DomainUserActivityType.STORY_PUBLISHED
                UserActivityType.AUTHOR_MENTION -> DomainUserActivityType.AUTHOR_MENTION
                UserActivityType.POST_COMMENT -> DomainUserActivityType.POST_COMMENT
                else -> throw Status.INVALID_ARGUMENT.withDescription("activity_type is required").asRuntimeException()
            }
            val result = notificationUseCases.publishUserActivity(
                sourceEventId = request.sourceEventId,
                actorId = UUID.fromString(request.actorId),
                activityType = activityType,
                entityType = request.entityType.ifBlank { null },
                entityId = request.entityId.ifBlank { null },
                metadataJson = request.metadataJson.ifBlank { "{}" }
            )
            PublishUserActivityResponse.newBuilder()
                .setSourceEventId(result.sourceEventId)
                .setAccepted(result.status == PublishActivityStatus.ACCEPTED)
                .setDuplicate(result.status == PublishActivityStatus.DUPLICATE)
                .build()
        }

    override fun getFollowersCount(request: UserIdRequest, responseObserver: StreamObserver<CountResponse>) =
        unary(responseObserver) {
            requireUserId()
            CountResponse.newBuilder().setCount(socialRepo.countFollowers(UUID.fromString(request.userId)).toLong()).build()
        }

    override fun getFollowingCount(request: UserIdRequest, responseObserver: StreamObserver<CountResponse>) =
        unary(responseObserver) {
            requireUserId()
            CountResponse.newBuilder().setCount(socialRepo.countFollowing(UUID.fromString(request.userId)).toLong()).build()
        }

    private fun <T> unary(observer: StreamObserver<T>, block: () -> T) {
        try {
            observer.onNext(block())
            observer.onCompleted()
        } catch (e: io.grpc.StatusRuntimeException) {
            observer.onError(e)
        } catch (e: IllegalArgumentException) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException())
        } catch (e: Exception) {
            observer.onError(e)
        }
    }

    private fun requireUserId(fallbackWhenAuthDisabled: String? = null): String {
        if (!requireUserToken) return fallbackWhenAuthDisabled ?: UUID.randomUUID().toString()
        return auth?.requireUserId()
            ?: throw Status.UNAUTHENTICATED.withDescription("access token is required").asRuntimeException()
    }

    private fun userService(): UserService = userService
        ?: throw Status.FAILED_PRECONDITION.withDescription("user service is not configured").asRuntimeException()

    private fun searchService(): SearchService = searchService
        ?: throw Status.FAILED_PRECONDITION.withDescription("search service is not configured").asRuntimeException()

    private fun privacyRepo(): PrivacyRepo = privacyRepo
        ?: throw Status.FAILED_PRECONDITION.withDescription("privacy repository is not configured").asRuntimeException()

    private fun User.toRelatedGrpc(relationship: profile.domain.Relationship): RelatedUser =
        RelatedUser.newBuilder().setUser(toGrpcUser()).setRelationship(relationship.toGrpc()).build()

    private fun User.toGrpcUser(): AccountUser {
        val builder = AccountUser.newBuilder()
            .setId(id)
            .setUsername(username)
            .setFirstName(firstName.orEmpty())
            .setLastName(lastName.orEmpty())
            .setAvatarUrl(avatarUrl.orEmpty())
            .setBio(bio.orEmpty())
            .addAllSocialLinks(socialLinks.map { it.toGrpc() })
        birthDate?.toBirthdayParts()?.let { builder.setBirthday(BirthdayParts.newBuilder().setDay(it.day).setMonth(it.month).build()) }
        return builder.build()
    }

    private fun DomainSocialLink.toGrpc(): SocialLink =
        SocialLink.newBuilder().setLabel(label).setUrl(url).build()

    private fun profile.domain.Relationship.toGrpc(): RelResponse =
        RelResponse.newBuilder()
            .setIsFollowing(isFollowing)
            .setIsFollowedBy(isFollowedBy)
            .setIsFriend(isFriend)
            .setIsBlocked(isBlocked)
            .setHasPendingRequest(hasPendingRequest)
            .build()

    private fun LocalizedText.toDomain(): profile.domain.LocalizedText =
        profile.domain.LocalizedText(ru = ru, en = en)
}
