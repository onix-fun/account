package profile.api.rest

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import profile.domain.*
import profile.infrastructure.*
import profile.usecases.*

fun Route.settingsRoutes(
    socialUseCases: SocialUseCases,
    notificationUseCases: NotificationUseCases
) {
    route("/api/profile/me") {
        get("/privacy") {
            val uid = requireUserId(call)
            val settings = socialUseCases.getPrivacySettings(uid)
            call.respond(settings.toResponse())
        }

        put("/privacy") {
            val uid = requireUserId(call)
            val body = call.receive<PrivacySettingsUpdateRequest>()
            socialUseCases.updatePrivacySettings(
                uid,
                body.isPrivate,
                body.fieldVisibility?.let { FieldVisibility.fromResponse(it) }
            )
            call.respond(SuccessResponse())
        }
    }

    route("/api/notifications") {
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
