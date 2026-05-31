package profile.infrastructure.db

import java.sql.Connection
import java.util.*
import javax.sql.DataSource

data class Session(
    val id: String,
    val userId: String,
    val refreshTokenHash: String,
    val deviceId: String? = null,
    val userAgent: String? = null,
    val ipAddress: String? = null,
    val expiresAt: java.time.Instant,
    val lastUsedAt: java.time.Instant = java.time.Instant.now(),
    val revokedAt: java.time.Instant? = null,
    val createdAt: java.time.Instant = java.time.Instant.now()
)

class SessionRepository(private val dataSource: DataSource) {
    companion object {
        private const val CREATE_SQL = """
            INSERT INTO sessions (id, user_id, refresh_token_hash, device_id, user_agent, ip_address, expires_at, last_used_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        private const val FIND_BY_TOKEN_HASH_SQL = "SELECT * FROM sessions WHERE refresh_token_hash = ? AND revoked_at IS NULL"
        private const val FIND_BY_USER_ID_SQL = "SELECT * FROM sessions WHERE user_id = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP"
        private const val FIND_ACTIVE_BY_IDS_SQL = "SELECT * FROM sessions WHERE id IN (%s) AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP"
        private const val REVOKE_SQL = "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?"
        private const val REVOKE_ALL_FOR_USER_SQL = "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL"
        private const val UPDATE_EXPIRATION_SQL = "UPDATE sessions SET expires_at = ?, last_used_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        private const val ROTATE_TOKEN_SQL = """
            UPDATE sessions
            SET refresh_token_hash = ?, expires_at = ?, last_used_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND refresh_token_hash = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
        """
        private const val FIND_BY_ID_SQL = "SELECT * FROM sessions WHERE id = ?"
    }

    fun create(session: Session) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(CREATE_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(session.id))
                stmt.setObject(2, UUID.fromString(session.userId))
                stmt.setString(3, session.refreshTokenHash)
                stmt.setString(4, session.deviceId)
                stmt.setString(5, session.userAgent)
                stmt.setString(6, session.ipAddress)
                stmt.setTimestamp(7, java.sql.Timestamp.from(session.expiresAt))
                stmt.setTimestamp(8, java.sql.Timestamp.from(session.lastUsedAt))
                stmt.setTimestamp(9, java.sql.Timestamp.from(session.createdAt))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun findByTokenHash(hash: String): Session? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_TOKEN_HASH_SQL).use { stmt ->
                stmt.setString(1, hash)
                val rs = stmt.executeQuery()
                if (rs.next()) return mapRow(rs)
            }
        }
        return null
    }

    fun findActiveByUserId(userId: String): List<Session> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_USER_ID_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                val rs = stmt.executeQuery()
                val result = mutableListOf<Session>()
                while (rs.next()) result.add(mapRow(rs))
                return result
            }
        }
    }

    fun findActiveByIds(ids: List<String>): List<Session> {
        if (ids.isEmpty()) return emptyList()
        dataSource.connection.use { conn ->
            val placeholders = ids.joinToString(",") { "?" }
            conn.prepareStatement(FIND_ACTIVE_BY_IDS_SQL.format(placeholders)).use { stmt ->
                ids.forEachIndexed { i, id -> stmt.setObject(i + 1, UUID.fromString(id)) }
                val rs = stmt.executeQuery()
                val result = mutableListOf<Session>()
                while (rs.next()) result.add(mapRow(rs))
                return result
            }
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

    fun rotateToken(sessionId: String, oldTokenHash: String, newTokenHash: String, expiresAt: java.time.Instant): Boolean {
        dataSource.connection.use { conn ->
            val updated = conn.prepareStatement(ROTATE_TOKEN_SQL).use { stmt ->
                stmt.setString(1, newTokenHash)
                stmt.setTimestamp(2, java.sql.Timestamp.from(expiresAt))
                stmt.setObject(3, UUID.fromString(sessionId))
                stmt.setString(4, oldTokenHash)
                stmt.executeUpdate()
            }
            conn.commit()
            return updated == 1
        }
    }

    fun findById(id: String): Session? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_ID_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(id))
                val rs = stmt.executeQuery()
                if (rs.next()) return mapRow(rs)
            }
        }
        return null
    }

    private fun mapRow(rs: java.sql.ResultSet): Session {
        return Session(
            id = rs.getObject("id").toString(),
            userId = rs.getObject("user_id").toString(),
            refreshTokenHash = rs.getString("refresh_token_hash"),
            deviceId = rs.getString("device_id"),
            userAgent = rs.getString("user_agent"),
            ipAddress = rs.getString("ip_address"),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            lastUsedAt = rs.getTimestamp("last_used_at").toInstant(),
            revokedAt = rs.getTimestamp("revoked_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
