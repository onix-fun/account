package com.onix.account.api.rest

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.onix.account.users.UserService
import com.onix.account.users.toPublicDto
import com.onix.account.domain.*
import com.onix.account.infrastructure.*
import com.onix.account.usecases.*
import java.util.UUID

fun Route.socialRoutes(
    userService: UserService,
    socialUseCases: SocialUseCases,
    notificationUseCases: NotificationUseCases,
    sseManager: SseManager
) {
    route("/api/profile") {
        post("/{id}/follow") {
            val actor = activeOwnerRef(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            val target = OwnerRef.user(targetId)
            val sub = socialUseCases.subscribe(actor, target)
            if (target.type == OwnerType.USER) {
                notificationUseCases.createSubscriptionCreatedNotification(sub)
                    ?.let { sseManager.push(sub.subscribedToId.toString(), it) }
            }
            val rel = socialUseCases.getRelationship(actor, target)
            call.respond(rel.toResponse())
        }

        delete("/{id}/follow") {
            val actor = activeOwnerRef(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            socialUseCases.removeSubscription(actor, OwnerRef.user(targetId))
            call.respond(SuccessResponse())
        }

        post("/owners/{ownerType}/{ownerId}/follow") {
            val actor = activeOwnerRef(call)
            val target = ownerRefFromParameters(call)
            val sub = socialUseCases.subscribe(actor, target)
            if (target.type == OwnerType.USER) {
                notificationUseCases.createSubscriptionCreatedNotification(sub)
                    ?.let { sseManager.push(sub.subscribedToId.toString(), it) }
            }
            call.respond(socialUseCases.getRelationship(actor, target).toResponse())
        }

        delete("/owners/{ownerType}/{ownerId}/follow") {
            socialUseCases.removeSubscription(activeOwnerRef(call), ownerRefFromParameters(call))
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
            val owner = activeOwnerRef(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            socialUseCases.blockOwner(owner, OwnerRef.user(targetId))
            call.respond(SuccessResponse())
        }

        delete("/{id}/block") {
            val owner = activeOwnerRef(call)
            val targetId = UUID.fromString(call.parameters["id"]!!)
            socialUseCases.unblockOwner(owner, OwnerRef.user(targetId))
            call.respond(SuccessResponse())
        }

        post("/owners/{ownerType}/{ownerId}/block") {
            socialUseCases.blockOwner(activeOwnerRef(call), ownerRefFromParameters(call))
            call.respond(SuccessResponse())
        }

        delete("/owners/{ownerType}/{ownerId}/block") {
            socialUseCases.unblockOwner(activeOwnerRef(call), ownerRefFromParameters(call))
            call.respond(SuccessResponse())
        }

        get("/me/blocked") {
            val owner = activeOwnerRef(call)
            val uid = requireUserId(call)
            val blocks = socialUseCases.getBlockedUsers(owner).filter { it.blockedType == OwnerType.USER }
            call.respond(blocks.mapNotNull {
                userService.getProfile(it.blockedId.toString())
                    ?.toPublicDto()
                    ?.withRelationship(socialUseCases.getRelationship(OwnerRef.user(uid), OwnerRef.user(it.blockedId)).toResponse())
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

}

private fun ownerRefFromParameters(call: ApplicationCall): OwnerRef {
    val ownerType = runCatching {
        OwnerType.valueOf(call.parameters["ownerType"].orEmpty().uppercase())
    }.getOrDefault(OwnerType.USER)
    val ownerId = call.parameters["ownerId"] ?: throw IllegalArgumentException("ownerId is required")
    return OwnerRef(ownerType, UUID.fromString(ownerId))
}
