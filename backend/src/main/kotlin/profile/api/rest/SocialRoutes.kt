package profile.api.rest

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import profile.users.UserService
import profile.users.toPublicDto
import profile.domain.*
import profile.infrastructure.*
import profile.usecases.*
import java.util.UUID

fun Route.socialRoutes(
    userService: UserService,
    socialUseCases: SocialUseCases,
    notificationUseCases: NotificationUseCases,
    sseManager: SseManager
) {
    route("/api/profile") {
        post("/{id}/follow") {
            val uid = requireUserId(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            val sub = socialUseCases.subscribe(uid, targetId)
            notificationUseCases.createSubscriptionCreatedNotification(sub)
                ?.let { sseManager.push(sub.subscribedToId.toString(), it) }
            val rel = socialUseCases.getRelationship(uid, targetId)
            call.respond(rel.toResponse())
        }

        delete("/{id}/follow") {
            val uid = requireUserId(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            socialUseCases.removeSubscription(uid, targetId)
            call.respond(SuccessResponse())
        }

        get("/{id}/followers") {
            val uid = requireUserId(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val (items, total) = socialUseCases.getFollowers(targetId, page, limit)
            call.respond(UserPageResponse(
                items = items.mapNotNull {
                    userService.getProfile(it.subscriberId.toString())
                        ?.toPublicDto()
                        ?.withRelationship(socialUseCases.getRelationship(uid, it.subscriberId).toResponse())
                },
                totalCount = total
            ))
        }

        get("/{id}/following") {
            val uid = requireUserId(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val (items, total) = socialUseCases.getFollowing(targetId, page, limit)
            call.respond(UserPageResponse(
                items = items.mapNotNull {
                    userService.getProfile(it.subscribedToId.toString())
                        ?.toPublicDto()
                        ?.withRelationship(socialUseCases.getRelationship(uid, it.subscribedToId).toResponse())
                },
                totalCount = total
            ))
        }

        get("/me/requests") {
            val uid = requireUserId(call)
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val (items, total) = socialUseCases.getPendingRequests(uid, page, limit)
            call.respond(SubscriptionPageResponse(
                items = items.map { sub ->
                    sub.toResponse(userService.getProfile(sub.subscriberId.toString())?.toPublicDto())
                },
                totalCount = total
            ))
        }

        post("/requests/{id}/accept") {
            val uid = requireUserId(call)
            val subscriberId = UUID.fromString(call.parameters["id"]!!)
            val sub = socialUseCases.acceptSubscription(subscriberId, uid)
            notificationUseCases.createSubscriptionAcceptedNotification(sub)
                ?.let { sseManager.push(sub.subscriberId.toString(), it) }
            call.respond(sub.toResponse())
        }

        delete("/requests/{id}/reject") {
            val uid = requireUserId(call)
            val subscriberId = UUID.fromString(call.parameters["id"]!!)
            socialUseCases.removeSubscription(subscriberId, uid)
            call.respond(SuccessResponse())
        }

        post("/{id}/block") {
            val uid = requireUserId(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            socialUseCases.blockUser(uid, targetId)
            call.respond(SuccessResponse())
        }

        delete("/{id}/block") {
            val uid = requireUserId(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            socialUseCases.unblockUser(uid, targetId)
            call.respond(SuccessResponse())
        }

        get("/me/blocked") {
            val uid = requireUserId(call)
            val blocks = socialUseCases.getBlockedUsers(uid)
            call.respond(blocks.mapNotNull {
                userService.getProfile(it.blockedId.toString())
                    ?.toPublicDto()
                    ?.withRelationship(socialUseCases.getRelationship(uid, it.blockedId).toResponse())
            })
        }

        get("/me/close-friends") {
            val uid = requireUserId(call)
            val friends = socialUseCases.getCloseFriends(uid)
            call.respond(friends.mapNotNull {
                userService.getProfile(it.subscribedToId.toString())
                    ?.toPublicDto()
                    ?.withRelationship(socialUseCases.getRelationship(uid, it.subscribedToId).toResponse())
            })
        }

        post("/me/close-friends") {
            val uid = requireUserId(call)
            val body = call.receive<Map<String, String>>()
            val targetId = UUID.fromString(body["userId"]!!)
            socialUseCases.markCloseFriend(uid, targetId)
            call.respond(SuccessResponse())
        }

        delete("/me/close-friends/{id}") {
            val uid = requireUserId(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            socialUseCases.unmarkCloseFriend(uid, targetId)
            call.respond(SuccessResponse())
        }
    }

    route("/api/internal") {
        get("/visibility") {
            val uid = requireUserId(call)
            val ownerId = UUID.fromString(call.request.queryParameters["ownerId"] ?: error("ownerId is required"))
            val viewerId = call.request.queryParameters["viewerId"]
                ?.takeIf(String::isNotBlank)
                ?.let(UUID::fromString)
                ?: uid
            require(viewerId == uid) { "viewerId must match authenticated user" }
            val relationship = socialUseCases.getRelationship(viewerId, ownerId)
            val privacy = socialUseCases.getPrivacySettings(ownerId)
            val isCloseFriend = socialUseCases.getCloseFriends(ownerId).any { it.subscribedToId == viewerId }
            call.respond(InternalVisibilityResponse(
                ownerId = ownerId.toString(),
                viewerId = viewerId.toString(),
                isPrivate = privacy.isPrivate,
                relationship = relationship.toResponse(),
                isBlocked = relationship.isBlocked,
                isCloseFriend = isCloseFriend
            ))
        }

        get("/social-graph") {
            val uid = requireUserId(call)
            val viewerId = call.request.queryParameters["viewerId"]
                ?.takeIf(String::isNotBlank)
                ?.let(UUID::fromString)
                ?: uid
            require(viewerId == uid) { "viewerId must match authenticated user" }
            val (following, _) = socialUseCases.getFollowing(viewerId, page = 1, limit = 1000)
            val (followers, _) = socialUseCases.getFollowers(viewerId, page = 1, limit = 1000)
            val followingIds = following.map { it.subscribedToId.toString() }
            val followerIds = followers.map { it.subscriberId.toString() }.toSet()
            call.respond(InternalSocialGraphResponse(
                followingIds = followingIds,
                friendIds = followingIds.filter { it in followerIds },
                blockedIds = socialUseCases.getBlockedUsers(viewerId).map { it.blockedId.toString() }
            ))
        }
    }
}
