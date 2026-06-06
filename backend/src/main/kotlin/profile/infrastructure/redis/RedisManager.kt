package profile.infrastructure.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.ktor.server.config.*
import java.util.concurrent.ConcurrentHashMap

class RedisManager(config: ApplicationConfig) {
    private val client: RedisClient?
    private val connection: StatefulRedisConnection<String, String>?
    private val memoryRateLimit = ConcurrentHashMap<String, Long>()

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
        val redis = sync()
        if (redis != null) {
            return redisCheckRateLimit(redis, scope, key, max, windowSeconds)
        }
        return memoryCheckRateLimit(scope, key, max, windowSeconds)
    }

    fun getAccountFailedAttempts(userId: String): Long {
        val redis = sync()
        val key = "profile:account-failures:$userId"
        if (redis != null) {
            val raw = redis.get(key)
            return raw?.toLongOrNull() ?: 0L
        }
        val windowKey = "profile:account-failures:$userId:${System.currentTimeMillis() / 300_000}"
        return memoryRateLimit.getOrDefault(windowKey, 0L)
    }

    fun incrementAccountFailedAttempts(userId: String): Long {
        val redis = sync()
        val key = "profile:account-failures:$userId"
        if (redis != null) {
            val count = redis.incr(key)
            if (count == 1L) redis.expire(key, 300)
            return count
        }
        val windowKey = "profile:account-failures:$userId:${System.currentTimeMillis() / 300_000}"
        return memoryRateLimit.merge(windowKey, 1L) { old, _ -> old + 1 } ?: 1L
    }

    fun clearAccountFailedAttempts(userId: String) {
        sync()?.del("profile:account-failures:$userId")
    }

    private val rateLimitSha: ThreadLocal<String> = ThreadLocal()

    private fun redisCheckRateLimit(redis: RedisCommands<String, String>, scope: String, key: String, max: Long, windowSeconds: Long): Boolean {
        val redisKey = "profile:rate:$scope:$key"
        try {
            var sha = rateLimitSha.get()
            try {
                return redis.evalsha<Boolean>(sha, io.lettuce.core.ScriptOutputType.BOOLEAN, arrayOf(redisKey), windowSeconds.toString(), max.toString())
            } catch (e: Exception) {
                sha = redis.scriptLoad("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
                    return current <= tonumber(ARGV[2])
                """.trimIndent())
                rateLimitSha.set(sha)
                return redis.evalsha<Boolean>(sha, io.lettuce.core.ScriptOutputType.BOOLEAN, arrayOf(redisKey), windowSeconds.toString(), max.toString())
            }
        } catch (e: Exception) {
            return memoryCheckRateLimit(scope, key, max, windowSeconds)
        }
    }

    private fun memoryCheckRateLimit(scope: String, key: String, max: Long, windowSeconds: Long): Boolean {
        val windowKey = "$scope:$key:${System.currentTimeMillis() / (windowSeconds * 1000)}"
        val value = memoryRateLimit.merge(windowKey, 1L) { old, _ -> old + 1 } ?: 1L
        return value <= max
    }

    fun close() {
        connection?.close()
        client?.shutdown()
    }
}
