package profile.infrastructure.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.ktor.server.config.*

class RedisManager(config: ApplicationConfig) {
    private val client: RedisClient?
    private val connection: StatefulRedisConnection<String, String>?

    init {
        val url = config.propertyOrNull("redis.url")?.getString()
        if (url != null) {
            val c = RedisClient.create(url)
            client = c
            connection = try {
                c.connect()
            } catch (e: Exception) {
                null
            }
        } else {
            client = null
            connection = null
        }
    }

    fun sync(): RedisCommands<String, String>? = connection?.sync()

    fun pubSubConnection(): io.lettuce.core.pubsub.StatefulRedisPubSubConnection<String, String>? = client?.connectPubSub()

    fun activateSession(sessionId: String, userId: String, ttlSeconds: Long) {
        sync()?.setex("profile:session:$sessionId", ttlSeconds, userId)
    }

    fun revokeSession(sessionId: String) { sync()?.del("profile:session:$sessionId") }

    fun checkRateLimit(scope: String, key: String, max: Long, windowSeconds: Long): Boolean {
        val redis = sync() ?: return true
        val redisKey = "profile:rate:$scope:$key"
        val count = redis.incr(redisKey)
        if (count == 1L) redis.expire(redisKey, windowSeconds)
        return count <= max
    }

    fun close() {
        connection?.close()
        client?.shutdown()
    }
}
