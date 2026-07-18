package com.onix.account.infrastructure.db

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class AccountLoginFailureRepository(private val dataSource: DataSource) {
    fun getAttempts(userId: String): Long {
        dataSource.connection.use { conn ->
            val attempts = conn.prepareStatement(GET_SQL).use {
                it.setObject(1, UUID.fromString(userId))
                val rs = it.executeQuery()
                if (rs.next()) rs.getLong("attempts") else 0L
            }
            conn.commit()
            return attempts
        }
    }

    fun increment(userId: String): Long {
        dataSource.connection.use { conn ->
            val attempts = conn.prepareStatement(UPSERT_SQL).use {
                it.setObject(1, UUID.fromString(userId))
                it.setTimestamp(2, Timestamp.from(Instant.now().plusSeconds(TTL_SECONDS)))
                it.setTimestamp(3, Timestamp.from(Instant.now().plusSeconds(TTL_SECONDS)))
                val rs = it.executeQuery()
                rs.next()
                rs.getLong("attempts")
            }
            conn.commit()
            return attempts
        }
    }

    fun clear(userId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(DELETE_SQL).use {
                it.setObject(1, UUID.fromString(userId))
                it.executeUpdate()
            }
            conn.commit()
        }
    }

    private companion object {
        private const val TTL_SECONDS = 900L
        private const val GET_SQL = """
            SELECT attempts FROM account_login_failures
            WHERE user_id = ? AND expires_at > CURRENT_TIMESTAMP
        """
        private const val UPSERT_SQL = """
            INSERT INTO account_login_failures (user_id, attempts, expires_at)
            VALUES (?, 1, ?)
            ON CONFLICT (user_id) DO UPDATE
            SET attempts = CASE
                    WHEN account_login_failures.expires_at <= CURRENT_TIMESTAMP THEN 1
                    ELSE account_login_failures.attempts + 1
                END,
                expires_at = ?,
                updated_at = CURRENT_TIMESTAMP
            RETURNING attempts
        """
        private const val DELETE_SQL = "DELETE FROM account_login_failures WHERE user_id = ?"
    }
}
