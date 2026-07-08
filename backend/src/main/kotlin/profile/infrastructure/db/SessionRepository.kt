package profile.infrastructure.db

import java.sql.Connection
import java.util.*
import javax.sql.DataSource

data class Session(
    val id: String = "",
    val userId: String,
    val refreshTokenHash: String,
    val previousRefreshTokenHash: String? = null,
    val refreshTokenRotatedAt: java.time.Instant? = null,
    val deviceId: String? = null,
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val expiresAt: java.time.Instant,
    val activeOwnerType: String = "USER",
    val activeOwnerId: String = userId,
    val lastUsedAt: java.time.Instant = java.time.Instant.now(),
    val revokedAt: java.time.Instant? = null,
    val createdAt: java.time.Instant = java.time.Instant.now()
)

class SessionRepository(private val dataSource: DataSource) {
    companion object {
        private const val REFRESH_TOKEN_GRACE_SECONDS = 30L
        private const val CREATE_SQL = """
            INSERT INTO sessions (user_id, refresh_token_hash, device_id, user_agent, ip_address, expires_at, active_owner_type, active_owner_id, last_used_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        """
        private const val FIND_BY_TOKEN_HASH_SQL = "SELECT * FROM sessions WHERE refresh_token_hash = ? AND revoked_at IS NULL"
        private const val FIND_BY_TOKEN_HASH_WITH_GRACE_SQL = """
            SELECT * FROM sessions
            WHERE (
                refresh_token_hash = ?
                OR (
                    previous_refresh_token_hash = ?
                    AND refresh_token_rotated_at > ?
                )
            )
            AND revoked_at IS NULL
        """
        private const val FIND_BY_USER_ID_SQL = "SELECT * FROM sessions WHERE user_id = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP"
        private const val FIND_ACTIVE_BY_IDS_SQL = "SELECT * FROM sessions WHERE id IN (%s) AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP"
        private const val REVOKE_SQL = "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?"
        private const val REVOKE_ALL_FOR_USER_SQL = "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL"
        private const val REVOKE_ALL_EXCEPT_SQL = "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND id <> ? AND revoked_at IS NULL"
        private const val UPDATE_EXPIRATION_SQL = "UPDATE sessions SET expires_at = ?, last_used_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        private const val ROTATE_TOKEN_SQL = """
            UPDATE sessions
            SET previous_refresh_token_hash = refresh_token_hash,
                refresh_token_hash = ?,
                refresh_token_rotated_at = CURRENT_TIMESTAMP,
                expires_at = ?,
                last_used_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND refresh_token_hash = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
        """
        private const val ROTATE_TOKEN_WITH_GRACE_SQL = """
            UPDATE sessions
            SET previous_refresh_token_hash = refresh_token_hash,
                refresh_token_hash = ?,
                refresh_token_rotated_at = CURRENT_TIMESTAMP,
                expires_at = ?,
                last_used_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
              AND (
                  refresh_token_hash = ?
                  OR (
                      previous_refresh_token_hash = ?
                      AND refresh_token_rotated_at > ?
                  )
              )
              AND revoked_at IS NULL
              AND expires_at > CURRENT_TIMESTAMP
        """
        private const val FIND_BY_ID_SQL = "SELECT * FROM sessions WHERE id = ?"
        private const val UPDATE_ACTIVE_OWNER_SQL = """
            UPDATE sessions
            SET active_owner_type = ?, active_owner_id = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND user_id = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
        """
    }

    fun create(session: Session): String {
        dataSource.connection.use { conn ->
            val id = conn.prepareStatement(CREATE_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(session.userId))
                stmt.setString(2, session.refreshTokenHash)
                stmt.setString(3, session.deviceId)
                stmt.setString(4, session.userAgent)
                stmt.setString(5, session.ipAddress)
                stmt.setTimestamp(6, java.sql.Timestamp.from(session.expiresAt))
                stmt.setString(7, session.activeOwnerType)
                stmt.setObject(8, UUID.fromString(session.activeOwnerId))
                stmt.setTimestamp(9, java.sql.Timestamp.from(session.lastUsedAt))
                stmt.setTimestamp(10, java.sql.Timestamp.from(session.createdAt))
                val rs = stmt.executeQuery()
                rs.next()
                rs.getObject("id").toString()
            }
            conn.commit()
            return id
        }
    }

    fun findByTokenHash(hash: String): Session? {
        dataSource.connection.use { conn ->
            val session = conn.prepareStatement(FIND_BY_TOKEN_HASH_SQL).use { stmt ->
                stmt.setString(1, hash)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            return session
        }
    }

    fun findByTokenHashWithGrace(hash: String): Session? {
        dataSource.connection.use { conn ->
            val session = conn.prepareStatement(FIND_BY_TOKEN_HASH_WITH_GRACE_SQL).use { stmt ->
                stmt.setString(1, hash)
                stmt.setString(2, hash)
                stmt.setTimestamp(3, java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(REFRESH_TOKEN_GRACE_SECONDS)))
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            return session
        }
    }

    fun findActiveByUserId(userId: String): List<Session> {
        dataSource.connection.use { conn ->
            val sessions = conn.prepareStatement(FIND_BY_USER_ID_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                val rs = stmt.executeQuery()
                val result = mutableListOf<Session>()
                while (rs.next()) result.add(mapRow(rs))
                result
            }
            conn.commit()
            return sessions
        }
    }

    fun findActiveByIds(ids: List<String>): List<Session> {
        if (ids.isEmpty()) return emptyList()
        dataSource.connection.use { conn ->
            val placeholders = ids.joinToString(",") { "?" }
            val sessions = conn.prepareStatement(FIND_ACTIVE_BY_IDS_SQL.format(placeholders)).use { stmt ->
                ids.forEachIndexed { i, id -> stmt.setObject(i + 1, UUID.fromString(id)) }
                val rs = stmt.executeQuery()
                val result = mutableListOf<Session>()
                while (rs.next()) result.add(mapRow(rs))
                result
            }
            conn.commit()
            return sessions
        }
    }

    fun revoke(sessionId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(REVOKE_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(sessionId))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun revokeAllForUser(userId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(REVOKE_ALL_FOR_USER_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun revokeAllExcept(userId: String, sessionId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(REVOKE_ALL_EXCEPT_SQL).use {
                it.setObject(1, UUID.fromString(userId)); it.setObject(2, UUID.fromString(sessionId)); it.executeUpdate()
            }; conn.commit()
        }
    }

    fun updateExpiration(sessionId: String, expiresAt: java.time.Instant) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPDATE_EXPIRATION_SQL).use { stmt ->
                stmt.setTimestamp(1, java.sql.Timestamp.from(expiresAt))
                stmt.setObject(2, UUID.fromString(sessionId))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun rotateToken(
        sessionId: String,
        oldTokenHash: String,
        newTokenHash: String,
        expiresAt: java.time.Instant,
        allowPreviousToken: Boolean = false
    ): Boolean {
        dataSource.connection.use { conn ->
            val sql = if (allowPreviousToken) ROTATE_TOKEN_WITH_GRACE_SQL else ROTATE_TOKEN_SQL
            val updated = conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, newTokenHash)
                stmt.setTimestamp(2, java.sql.Timestamp.from(expiresAt))
                stmt.setObject(3, UUID.fromString(sessionId))
                stmt.setString(4, oldTokenHash)
                if (allowPreviousToken) {
                    stmt.setString(5, oldTokenHash)
                    stmt.setTimestamp(
                        6,
                        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(REFRESH_TOKEN_GRACE_SECONDS))
                    )
                }
                stmt.executeUpdate()
            }
            conn.commit()
            return updated == 1
        }
    }

    fun findById(id: String): Session? {
        val uuid = runCatching { UUID.fromString(id.trim()) }.getOrNull()
            ?: runCatching { UUID.fromString(id.trim().take(36)) }.getOrNull()
            ?: return null

        dataSource.connection.use { conn ->
            val session = conn.prepareStatement(FIND_BY_ID_SQL).use { stmt ->
                stmt.setObject(1, uuid)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            return session
        }
    }

    fun updateActiveOwner(sessionId: String, userId: String, ownerType: String, ownerId: String): Boolean {
        dataSource.connection.use { conn ->
            val updated = conn.prepareStatement(UPDATE_ACTIVE_OWNER_SQL).use { stmt ->
                stmt.setString(1, ownerType)
                stmt.setObject(2, UUID.fromString(ownerId))
                stmt.setObject(3, UUID.fromString(sessionId))
                stmt.setObject(4, UUID.fromString(userId))
                stmt.executeUpdate()
            }
            conn.commit()
            return updated == 1
        }
    }

    private fun mapRow(rs: java.sql.ResultSet): Session {
        return Session(
            id = rs.getObject("id").toString(),
            userId = rs.getObject("user_id").toString(),
            refreshTokenHash = rs.getString("refresh_token_hash"),
            previousRefreshTokenHash = rs.getString("previous_refresh_token_hash"),
            refreshTokenRotatedAt = rs.getTimestamp("refresh_token_rotated_at")?.toInstant(),
            deviceId = rs.getString("device_id"),
            userAgent = rs.getString("user_agent"),
            ipAddress = rs.getString("ip_address"),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            activeOwnerType = runCatching { rs.getString("active_owner_type") }.getOrNull() ?: "USER",
            activeOwnerId = runCatching { rs.getObject("active_owner_id")?.toString() }.getOrNull() ?: rs.getObject("user_id").toString(),
            lastUsedAt = rs.getTimestamp("last_used_at").toInstant(),
            revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
