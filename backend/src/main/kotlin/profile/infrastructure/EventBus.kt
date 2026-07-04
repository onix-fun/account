package profile.infrastructure

import profile.usecases.NotificationUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.UUID

data class DomainEvent(
    val eventType: String,
    val payload: Map<String, String?>
)

class EventBus(
    private val notificationUseCases: NotificationUseCases,
    private val sseManager: SseManager
) {
    private val channel = Channel<DomainEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            for (event in channel) {
                try {
                    process(event)
                } catch (e: Exception) {
                    System.err.println("EventBus error: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        channel.close()
    }

    fun publish(event: DomainEvent) {
        channel.trySend(event)
    }

    private fun process(event: DomainEvent) {
        when (event.eventType) {
            "comment.created" -> handleCommentCreated(event.payload)
        }
    }

    private fun handleCommentCreated(p: Map<String, String?>) {
        val postOwner = parseUuid(p["postOwnerId"]) ?: return
        val author = parseUuid(p["authorId"]) ?: return
        val postId = p["postId"] ?: return
        val eventId = p["eventId"] ?: return

        notificationUseCases.createFromEvent(
            eventId = eventId, recipientId = postOwner, type = "post_comment",
            title = "New comment", body = "Someone commented on your post",
            actorId = author, entityType = "post", entityId = postId
        ).let { if (it != null) sseManager.push(postOwner.toString(), it) }
    }

    private fun parseUuid(s: String?): UUID? = s?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}
