package com.onix.account.infrastructure.db

import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class RateLimitRepository(private val dataSource: DataSource) {
    fun checkAndIncrement(scope: String, key: String, max: Long, windowSeconds: Long): Boolean {
        val now = Instant.now()
        val windowStart = Instant.ofEpochSecond((now.epochSecond / windowSeconds) * windowSeconds)
        val expiresAt = windowStart.plusSeconds(windowSeconds)
        dataSource.connection.use { conn ->
            val count = conn.prepareStatement(UPSERT_SQL).use {
                it.setString(1, scope)
                it.setString(2, keyHash(scope, key))
                it.setTimestamp(3, Timestamp.from(windowStart))
                it.setInt(4, windowSeconds.toInt())
                it.setTimestamp(5, Timestamp.from(expiresAt))
                it.setTimestamp(6, Timestamp.from(expiresAt))
                val rs = it.executeQuery()
                rs.next()
                rs.getLong("count")
            }
            conn.commit()
            return count <= max
        }
    }

    private fun keyHash(scope: String, key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$scope\u0000$key".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val UPSERT_SQL = """
            INSERT INTO rate_limit_counters (scope, key_hash, window_start, window_seconds, count, expires_at)
            VALUES (?, ?, ?, ?, 1, ?)
            ON CONFLICT (scope, key_hash, window_start) DO UPDATE
            SET count = rate_limit_counters.count + 1,
                expires_at = ?,
                updated_at = CURRENT_TIMESTAMP
            RETURNING count
        """
    }
}
