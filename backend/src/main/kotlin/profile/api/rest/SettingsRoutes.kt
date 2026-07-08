package profile.api.rest

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import profile.domain.*
import profile.infrastructure.*
import profile.usecases.*
import profile.users.UserService

fun Route.settingsRoutes(
    socialUseCases: SocialUseCases,
    notificationUseCases: NotificationUseCases,
    userService: UserService
) {
    route("/api/profile/me") {
        get("/privacy") {
            val settings = socialUseCases.getPrivacySettings(activeOwnerRef(call))
            call.respond(settings.toResponse())
        }

        put("/privacy") {
            val owner = activeOwnerRef(call)
            val body = call.receive<PrivacySettingsUpdateRequest>()
            socialUseCases.updatePrivacySettings(
                owner,
                body.isPrivate,
                body.fieldVisibility?.let { FieldVisibility.fromResponse(it) }
            )
            call.respond(SuccessResponse())
        }
    }

    route("/api/notifications") {
        get("/settings") {
            val uid = requireUserId(call)
            val locale = userService.getProfile(uid.toString())?.preferredLocale
                ?: call.request.headers["Accept-Language"].orEmpty()
            call.respond(NotificationSettingsResponse(
                services = notificationUseCases.getLocalizedSettings(uid, locale).map { it.toResponse() }
            ))
        }

        put("/settings") {
            val uid = requireUserId(call)
            val body = call.receive<NotificationPreferenceUpdateRequest>()
            notificationUseCases.savePreference(uid, body.serviceKey, body.typeKey, body.enabled)
            call.respond(SuccessResponse())
        }

        get("/preferences") {
            val uid = requireUserId(call)
            val prefs = notificationUseCases.getPrefs(uid)
            call.respond(prefs.toResponse())
        }

        put("/preferences") {
            val uid = requireUserId(call)
            val body = call.receive<Map<String, Boolean>>()
            notificationUseCases.savePrefs(NotificationPrefs(
                userId = uid,
                inAppSubscriptions = body["inAppSubscriptions"] ?: true,
                inAppPublications = body["inAppPublications"] ?: true,
                inAppAuthorMentions = body["inAppAuthorMentions"] ?: true,
                inAppPostComments = body["inAppPostComments"] ?: true,
                inAppNewStories = body["inAppNewStories"] ?: true,
                inAppBirthdays = body["inAppBirthdays"] ?: true,
            ))
            call.respond(SuccessResponse())
        }
    }
}
