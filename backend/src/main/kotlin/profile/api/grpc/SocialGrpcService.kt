package profile.api.grpc

import com.unlim.profile.grpc.v1.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import profile.domain.NotificationServiceCatalog
import profile.domain.NotificationTypeCatalog
import profile.domain.OwnerRef as DomainOwnerRef
import profile.domain.ProfileVisibility
import profile.domain.PublishActivityStatus
import profile.domain.SocialLink as DomainSocialLink
import profile.domain.UserActivityType as DomainUserActivityType
import profile.infrastructure.PrivacyRepo
import profile.infrastructure.SseManager
import profile.infrastructure.db.User
import profile.organizations.OrganizationService
import profile.organizations.OwnerAction as DomainOwnerAction
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
    private val organizationService: OrganizationService?,
    private val requireUserToken: Boolean = true,
) : ProfileServiceGrpc.ProfileServiceImplBase() {

    constructor(
        socialUseCases: SocialUseCases,
        blockRepo: BlockRepository,
        socialRepo: SocialRepository,
        notificationUseCases: NotificationUseCases
    ) : this(null, null, socialUseCases, blockRepo, socialRepo, null, notificationUseCases, SseManager(), null, null, false)

    override fun getCurrentUser(request: CurrentUserRequest, responseObserver: StreamObserver<AccountUser>) =
        unary(responseObserver) {
            val userId = requireUserId()
            val user = userService().getProfile(userId) ?: throw Status.NOT_FOUND.asRuntimeException()
            user.toGrpcUser()
        }

    override fun getCurrentActor(request: CurrentUserRequest, responseObserver: StreamObserver<CurrentActor>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val user = userService().getProfile(principal.userId) ?: throw Status.NOT_FOUND.asRuntimeException()
            val owner = organizationService().findOwner(principal.activeOwnerRef())
                ?: throw Status.NOT_FOUND.withDescription("active owner not found").asRuntimeException()
            CurrentActor.newBuilder()
                .setUser(user.toGrpcUser())
                .setActiveOwner(owner.toGrpc())
                .build()
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

    override fun getOrganizationByName(request: GetOrganizationByNameRequest, responseObserver: StreamObserver<OwnerProfile>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val org = organizationService().findByName(request.orgName)
                ?: throw Status.NOT_FOUND.withDescription("organization not found").asRuntimeException()
            ownerProfile(
                owner = DomainOwnerRef(profile.domain.OwnerType.ORGANIZATION, UUID.fromString(org.id)),
                viewer = principal.activeOwnerRef()
            )
        }

    override fun getOwnerByRef(request: GetOwnerByRefRequest, responseObserver: StreamObserver<OwnerProfile>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            ownerProfile(request.owner.toDomain(), principal.activeOwnerRef())
        }

    override fun searchOwners(request: SearchUsersRequest, responseObserver: StreamObserver<OwnerListResponse>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val users = searchService().searchByUsernamePrefix(request.query, request.limit.coerceIn(1, 25))
                .map { ownerProfile(DomainOwnerRef(profile.domain.OwnerType.USER, UUID.fromString(it.id)), principal.activeOwnerRef()) }
            val orgs = organizationService().search(request.query, request.limit.coerceIn(1, 25))
                .map { ownerProfile(DomainOwnerRef(profile.domain.OwnerType.ORGANIZATION, UUID.fromString(it.id)), principal.activeOwnerRef()) }
            OwnerListResponse.newBuilder().addAllOwners(users + orgs).build()
        }

    override fun authorizeOwnerAction(request: AuthorizeOwnerActionRequest, responseObserver: StreamObserver<AuthorizeOwnerActionResponse>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val action = when (request.action) {
                OwnerAction.CREATE_CONTENT -> DomainOwnerAction.CREATE_CONTENT
                OwnerAction.MANAGE_ORGANIZATION -> DomainOwnerAction.MANAGE_ORGANIZATION
                OwnerAction.MANAGE_MEMBERS -> DomainOwnerAction.MANAGE_MEMBERS
                OwnerAction.ACT_AS_OWNER -> DomainOwnerAction.ACT_AS_OWNER
                else -> throw Status.INVALID_ARGUMENT.withDescription("owner action is required").asRuntimeException()
            }
            AuthorizeOwnerActionResponse.newBuilder()
                .setAllowed(organizationService().authorize(principal.userId, request.owner.toDomain(), action))
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

    override fun followOwner(request: OwnerFollowRequest, responseObserver: StreamObserver<RelResponse>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val subscriber = principal.activeOwnerRef()
            val target = request.target.toDomain()
            socialUseCases.subscribe(subscriber, target)
            socialUseCases.getRelationship(subscriber, target).toGrpc()
        }

    override fun unfollow(request: FollowRequest, responseObserver: StreamObserver<Empty>) =
        unary(responseObserver) {
            val userId = UUID.fromString(requireUserId())
            socialUseCases.removeSubscription(userId, UUID.fromString(request.targetId))
            Empty.getDefaultInstance()
        }

    override fun unfollowOwner(request: OwnerFollowRequest, responseObserver: StreamObserver<Empty>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            socialUseCases.removeSubscription(principal.activeOwnerRef(), request.target.toDomain())
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

    override fun listOwnerFollowers(request: OwnerPageRequest, responseObserver: StreamObserver<OwnerPageResponse>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val target = request.owner.toDomain()
            val (items, total) = socialUseCases.getFollowers(target, request.page.coerceAtLeast(1), request.limit.coerceIn(1, 100))
            OwnerPageResponse.newBuilder()
                .setTotalCount(total)
                .addAllItems(items.map { sub ->
                    RelatedOwner.newBuilder()
                        .setOwner(ownerProfile(DomainOwnerRef(sub.subscriberType, sub.subscriberId), principal.activeOwnerRef()))
                        .setRelationship(socialUseCases.getRelationship(principal.activeOwnerRef(), DomainOwnerRef(sub.subscriberType, sub.subscriberId)).toGrpc())
                        .build()
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

    override fun listOwnerFollowing(request: OwnerPageRequest, responseObserver: StreamObserver<OwnerPageResponse>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val target = request.owner.toDomain()
            val (items, total) = socialUseCases.getFollowing(target, request.page.coerceAtLeast(1), request.limit.coerceIn(1, 100))
            OwnerPageResponse.newBuilder()
                .setTotalCount(total)
                .addAllItems(items.map { sub ->
                    RelatedOwner.newBuilder()
                        .setOwner(ownerProfile(DomainOwnerRef(sub.subscribedToType, sub.subscribedToId), principal.activeOwnerRef()))
                        .setRelationship(socialUseCases.getRelationship(principal.activeOwnerRef(), DomainOwnerRef(sub.subscribedToType, sub.subscribedToId)).toGrpc())
                        .build()
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

    override fun getOwnerVisibility(request: OwnerVisibilityRequest, responseObserver: StreamObserver<VisibilityResponse>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val viewer = request.viewer.takeIf { it.ownerId.isNotBlank() }?.toDomain() ?: principal.activeOwnerRef()
            if (viewer != principal.activeOwnerRef()) {
                throw Status.PERMISSION_DENIED.withDescription("viewer must match active owner").asRuntimeException()
            }
            val owner = request.owner.toDomain()
            val relationship = socialUseCases.getRelationship(viewer, owner)
            val isPrivate = if (owner.type == profile.domain.OwnerType.USER) {
                socialUseCases.getPrivacySettings(owner.id).isPrivate
            } else {
                false
            }
            VisibilityResponse.newBuilder()
                .setOwnerId(owner.id.toString())
                .setViewerId(viewer.id.toString())
                .setIsPrivate(isPrivate)
                .setRelationship(relationship.toGrpc())
                .setIsBlocked(relationship.isBlocked)
                .setIsCloseFriend(false)
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

    override fun getOwnerSocialGraph(request: OwnerRef, responseObserver: StreamObserver<SocialGraphResponse>) =
        unary(responseObserver) {
            val principal = requirePrincipal()
            val owner = request.toDomain()
            if (owner != principal.activeOwnerRef()) {
                throw Status.PERMISSION_DENIED.withDescription("owner must match active owner").asRuntimeException()
            }
            val (following, _) = socialUseCases.getFollowing(owner, page = 1, limit = 1000)
            val (followers, _) = socialUseCases.getFollowers(owner, page = 1, limit = 1000)
            val followingKeys = following.map { "${it.subscribedToType}:${it.subscribedToId}" }
            val followerKeys = followers.map { "${it.subscriberType}:${it.subscriberId}" }.toSet()
            SocialGraphResponse.newBuilder()
                .addAllFollowingIds(followingKeys)
                .addAllFriendIds(followingKeys.filter { it in followerKeys })
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

    private fun requirePrincipal(fallbackWhenAuthDisabled: String? = null): GrpcPrincipal {
        if (!requireUserToken) {
            val id = fallbackWhenAuthDisabled ?: UUID.randomUUID().toString()
            return GrpcPrincipal(id, "", profile.domain.OwnerType.USER.name, id)
        }
        return auth?.requirePrincipal()
            ?: throw Status.UNAUTHENTICATED.withDescription("access token is required").asRuntimeException()
    }

    private fun userService(): UserService = userService
        ?: throw Status.FAILED_PRECONDITION.withDescription("user service is not configured").asRuntimeException()

    private fun searchService(): SearchService = searchService
        ?: throw Status.FAILED_PRECONDITION.withDescription("search service is not configured").asRuntimeException()

    private fun privacyRepo(): PrivacyRepo = privacyRepo
        ?: throw Status.FAILED_PRECONDITION.withDescription("privacy repository is not configured").asRuntimeException()

    private fun organizationService(): OrganizationService = organizationService
        ?: throw Status.FAILED_PRECONDITION.withDescription("organization service is not configured").asRuntimeException()

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

    private fun ownerProfile(owner: DomainOwnerRef, viewer: DomainOwnerRef?): OwnerProfile {
        val identity = organizationService().findOwner(owner)
            ?: throw Status.NOT_FOUND.withDescription("owner not found").asRuntimeException()
        val relationship = socialUseCases.getRelationship(viewer, owner)
        val isPrivate = if (owner.type == profile.domain.OwnerType.USER) {
            socialUseCases.getPrivacySettings(owner.id).isPrivate
        } else {
            false
        }
        val user = if (owner.type == profile.domain.OwnerType.USER) userService().getProfile(owner.id.toString()) else null
        val organization = if (owner.type == profile.domain.OwnerType.ORGANIZATION) organizationService().findByName(identity.username) else null
        val bio = when (owner.type) {
            profile.domain.OwnerType.USER -> user?.bio
            profile.domain.OwnerType.ORGANIZATION -> organization?.bio
        }.orEmpty()
        val socialLinks = when (owner.type) {
            profile.domain.OwnerType.USER -> user?.socialLinks
            profile.domain.OwnerType.ORGANIZATION -> organization?.socialLinks
        }.orEmpty()
        return OwnerProfile.newBuilder()
            .setOwner(identity.toGrpc())
            .setBio(bio)
            .addAllSocialLinks(socialLinks.map { it.toGrpc() })
            .setFollowersCount(socialRepo.countFollowers(owner))
            .setFollowingCount(socialRepo.countFollowing(owner))
            .setIsPrivate(isPrivate)
            .setRelationship(relationship.toGrpc())
            .build()
    }

    private fun profile.domain.OwnerIdentityDto.toGrpc(): OwnerIdentity =
        OwnerIdentity.newBuilder()
            .setRef(OwnerRef.newBuilder().setOwnerType(ownerType.toGrpc()).setOwnerId(ownerId).build())
            .setUsername(username)
            .setDisplayName(displayName)
            .setAvatarUrl(avatarUrl.orEmpty())
            .setRole(role?.name.orEmpty())
            .build()

    private fun profile.domain.OwnerType.toGrpc(): OwnerType =
        when (this) {
            profile.domain.OwnerType.USER -> OwnerType.USER
            profile.domain.OwnerType.ORGANIZATION -> OwnerType.ORGANIZATION
        }

    private fun OwnerRef.toDomain(): profile.domain.OwnerRef {
        val type = when (ownerType) {
            OwnerType.ORGANIZATION -> profile.domain.OwnerType.ORGANIZATION
            else -> profile.domain.OwnerType.USER
        }
        return profile.domain.OwnerRef(type, UUID.fromString(ownerId))
    }

    private fun GrpcPrincipal.activeOwnerRef(): DomainOwnerRef =
        DomainOwnerRef(
            type = runCatching { profile.domain.OwnerType.valueOf(activeOwnerType) }.getOrDefault(profile.domain.OwnerType.USER),
            id = UUID.fromString(activeOwnerId)
        )

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
