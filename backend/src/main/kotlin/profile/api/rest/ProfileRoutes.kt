package profile.api.rest

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import profile.users.UserService
import profile.users.toPublicDto
import profile.infrastructure.*
import profile.usecases.*
import profile.domain.*
import java.util.UUID

fun Route.profileRoutes(
    userService: UserService,
    socialRepo: SocialRepo,
    privacyRepo: PrivacyRepo,
    socialUseCases: SocialUseCases,
    notificationRepo: NotificationRepo
) {
    route("/api/profile") {
        get("/me") {
            val uid = requireUserId(call)
            val user = userService.getProfile(uid.toString())
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                return@get
            }
            val followersCount = socialRepo.countFollowers(uid)
            val followingCount = socialRepo.countFollowing(uid)
            val privacy = privacyRepo.get(uid)
            val unreadCount = notificationRepo.countUnread(uid)
            val pendingCount = socialUseCases.getPendingRequests(uid, 1, 1).second

            call.respond(ProfileSummaryResponse(
                id = user.id,
                username = user.username,
                firstName = user.firstName,
                lastName = user.lastName,
                bio = user.bio,
                avatarUrl = user.avatarUrl,
                followersCount = followersCount,
                followingCount = followingCount,
                isPrivate = privacy.isPrivate,
                unreadNotificationCount = unreadCount,
                pendingRequestsCount = pendingCount
            ))
        }

        get("/{username}") {
            val currentUserId = requireUserId(call)
            val username = call.parameters["username"] ?: return@get call.respond(HttpStatusCode.NotFound)
            val user = userService.getProfileByUsername(username)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                return@get
            }

            val targetId = UUID.fromString(user.id)
            val followersCount = socialRepo.countFollowers(targetId)
            val followingCount = socialRepo.countFollowing(targetId)
            val privacy = privacyRepo.get(targetId)
            val relationship = socialUseCases.getRelationship(currentUserId, targetId)

            call.respond(PublicProfileResponse(
                id = user.id,
                username = user.username,
                firstName = user.firstName,
                lastName = user.lastName,
                bio = user.bio,
                avatarUrl = user.avatarUrl,
                followersCount = followersCount,
                followingCount = followingCount,
                isPrivate = privacy.isPrivate,
                relationship = relationship.toResponse()
            ))
        }
    }
}
