package com.onix.account.api.rest

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import com.onix.account.users.UserService
import com.onix.account.domain.*
import com.onix.account.infrastructure.*
import com.onix.account.usecases.*
import java.util.UUID

fun Route.notificationRoutes(
    userService: UserService,
    notificationUseCases: NotificationUseCases,
    sseManager: SseManager
) {
    route("/api/notifications") {
        get {
            val uid = requireUserId(call)
            val locale = userService.getProfile(uid.toString())?.preferredLocale
                ?: call.request.headers["Accept-Language"].orEmpty()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val (items, total) = notificationUseCases.getNotifications(uid, page, limit)
            call.respond(NotificationPageResponse(
                items = items.map { it.localized(locale).toResponse(normalizedMetadata(it)) },
                totalCount = total
            ))
        }

        get("/unread") {
            val uid = requireUserId(call)
            val count = notificationUseCases.getUnreadCount(uid)
            call.respond(CountResponse(count))
        }

        put("/{id}/read") {
            val uid = requireUserId(call)
            val id = UUID.fromString(call.parameters["id"]!!)
            notificationUseCases.markRead(id)
            val count = notificationUseCases.getUnreadCount(uid)
            sseManager.pushUnreadCount(uid.toString(), count)
            call.respond(SuccessResponse())
        }

        put("/read-all") {
            val uid = requireUserId(call)
            notificationUseCases.markAllRead(uid)
            sseManager.pushUnreadCount(uid.toString(), 0)
            call.respond(SuccessResponse())
        }

        get("/stream") {
            val uid = requireUserId(call)
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.response.header(HttpHeaders.Connection, "keep-alive")

            val ch = sseManager.subscribe(uid.toString())
            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                try {
                    while (true) {
                        val msg = ch.receive()
                        val eventType = if (msg.contains("\"type\":\"unread-count\"")) "unread-count" else "new"
                        writeStringUtf8("event: $eventType\n")
                        writeStringUtf8("data: $msg\n\n")
                        flush()
                    }
                } finally {
                    sseManager.unsubscribe(uid.toString(), ch)
                }
            }
        }
    }
}

private val notificationMetadataJson = Json { ignoreUnknownKeys = true }

private fun Notification.localized(locale: String): Notification {
    val normalized = if (locale.lowercase().startsWith("ru")) "ru" else "en"
    return copy(
        title = localizedText(titleI18nJson, normalized) ?: title,
        body = localizedText(bodyI18nJson, normalized) ?: body
    )
}

private fun localizedText(raw: String?, locale: String): String? {
    if (raw.isNullOrBlank()) return null
    val obj = runCatching { notificationMetadataJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
    return obj[locale]?.jsonPrimitive?.contentOrNull
        ?: obj["en"]?.jsonPrimitive?.contentOrNull
        ?: obj["ru"]?.jsonPrimitive?.contentOrNull
}

private fun normalizedMetadata(n: Notification): NotificationMetadataResponse {
    val actions = mutableListOf<NotificationActionResponse>()
    var href: String? = null
    var titleKey: String? = null
    var bodyKey: String? = null

    runCatching {
        val root = notificationMetadataJson.parseToJsonElement(n.metadataJson)
        if (root is JsonObject) {
            href = root["href"]?.jsonPrimitive?.contentOrNull
            titleKey = root["titleKey"]?.jsonPrimitive?.contentOrNull
            bodyKey = root["bodyKey"]?.jsonPrimitive?.contentOrNull
            root["actions"]?.jsonArray?.forEach { item ->
                val action = item as? JsonObject ?: return@forEach
                val kind = action["kind"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                when (kind) {
                    "accept_follow", "reject_follow" -> {
                        val targetUserId = action["targetUserId"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        actions.add(NotificationActionResponse(kind = kind, targetUserId = targetUserId))
                    }
                    "accept_organization_invitation", "decline_organization_invitation" -> {
                        val invitationId = action["invitationId"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        actions.add(NotificationActionResponse(kind = kind, invitationId = invitationId))
                    }
                    "open_url" -> {
                        val actionHref = action["href"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        actions.add(NotificationActionResponse(kind = kind, href = actionHref))
                    }
                }
            }
        }
    }

    if (n.typeKey == "subscription_request" && n.actorId != null) {
        val targetUserId = n.actorId.toString()
        if (actions.none { it.kind == "accept_follow" && it.targetUserId == targetUserId }) {
            actions.add(NotificationActionResponse(kind = "accept_follow", targetUserId = targetUserId))
        }
        if (actions.none { it.kind == "reject_follow" && it.targetUserId == targetUserId }) {
            actions.add(NotificationActionResponse(kind = "reject_follow", targetUserId = targetUserId))
        }
    }

    return NotificationMetadataResponse(href = href, titleKey = titleKey, bodyKey = bodyKey, actions = actions)
}
