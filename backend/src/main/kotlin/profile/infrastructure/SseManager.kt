package profile.infrastructure

import profile.domain.Notification
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

class SseManager {
    private val connections = ConcurrentHashMap<String, MutableSet<Channel<String>>>()

    fun subscribe(userId: String): Channel<String> {
        val ch = Channel<String>(Channel.UNLIMITED)
        connections.getOrPut(userId) { mutableSetOf() }.add(ch)
        return ch
    }

    fun unsubscribe(userId: String, ch: Channel<String>) {
        connections[userId]?.remove(ch)
        if (connections[userId]?.isEmpty() == true) connections.remove(userId)
    }

    fun push(userId: String, notification: Notification) {
        val data = "{\"type\":\"new\",\"id\":\"${notification.id}\",\"title\":\"${notification.title}\"}"
        connections[userId]?.forEach { ch ->
            ch.trySend(data)
        }
    }

    fun pushUnreadCount(userId: String, count: Int) {
        val data = "{\"type\":\"unread-count\",\"count\":$count,\"unreadCount\":$count}"
        connections[userId]?.forEach { ch ->
            ch.trySend(data)
        }
    }
}
