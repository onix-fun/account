package profile.infrastructure.events

import profile.infrastructure.redis.RedisManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class DomainEvent(
    val type: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class EventPublisher(private val redisManager: RedisManager) {
    fun publish(type: String, payload: String) {
        val event = DomainEvent(type, payload)
        val jsonEvent = Json.encodeToString(event)
        try {
            redisManager.sync()?.publish("identity_events", jsonEvent)
        } catch (e: Exception) {
            // Log error but don't crash the request
            System.err.println("Failed to publish event: ${e.message}")
        }
    }
}
