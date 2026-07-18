package com.onix.account.infrastructure.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import com.onix.account.infrastructure.config.AppConfig

class RedisManager(config: AppConfig) {
    private val client: RedisClient?
    private val connection: StatefulRedisConnection<String, String>?

    init {
        val url = config.redis.url
        println("DEBUG: Connecting to Redis at $url")
        val c = RedisClient.create(url)
        c.setOptions(io.lettuce.core.ClientOptions.builder()
            .socketOptions(io.lettuce.core.SocketOptions.builder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build())
            .build())
        client = c
        connection = try {
            c.connect().also { println("DEBUG: Redis connected successfully") }
        } catch (e: Exception) {
            println("DEBUG: Redis connection failed: ${e.message}")
            null
        }
    }

    fun sync(): RedisCommands<String, String>? = connection?.sync()

    fun isReady(): Boolean = runCatching { sync()?.ping() == "PONG" }.getOrDefault(false)

    fun close() {
        connection?.close()
        client?.shutdown()
    }
}
