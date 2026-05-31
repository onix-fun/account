package profile.infrastructure.db

import java.sql.Connection
import java.time.Instant
import java.util.*
import javax.sql.DataSource

data class VerificationToken(
    val id: String,
    val userId: String,
    val tokenHash: String,
    val purpose: String,
    val expiresAt: Instant,
    val usedAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

class VerificationTokenRepository(private val dataSource: DataSource) {
    companion object {
        private const val CREATE_SQL = "INSERT INTO verification_tokens (id, user_id, token_hash, purpose, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        private const val FIND_BY_HASH_SQL = "SELECT * FROM verification_tokens WHERE token_hash = ? AND used_at IS NULL"
        private const val MARK_AS_USED_SQL = "UPDATE verification_tokens SET used_at = ? WHERE id = ?"
        private const val DELETE_EXPIRED_SQL = "DELETE FROM verification_tokens WHERE expires_at < ?"
    }

    fun create(token: VerificationToken) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(CREATE_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(token.id))
                stmt.setObject(2, UUID.fromString(token.userId))
                stmt.setString(3, token.tokenHash)
                stmt.setString(4, token.purpose)
                stmt.setTimestamp(5, java.sql.Timestamp.from(token.expiresAt))
                stmt.setTimestamp(6, java.sql.Timestamp.from(token.createdAt))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun findByHash(hash: String): VerificationToken? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_HASH_SQL).use { stmt ->
                stmt.setString(1, hash)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return VerificationToken(
                        id = rs.getObject("id").toString(),
                        userId = rs.getObject("user_id").toString(),
                        tokenHash = rs.getString("token_hash"),
                        purpose = rs.getString("purpose"),
                        expiresAt = rs.getTimestamp("expires_at").toInstant(),
                        usedAt = rs.getTimestamp("used_at")?.toInstant(),
                        createdAt = rs.getTimestamp("created_at").toInstant()
                    )
                }
            }
        }
        return null
    }

    fun markAsUsed(id: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(MARK_AS_USED_SQL).use { stmt ->
                stmt.setTimestamp(1, java.sql.Timestamp.from(Instant.now()))
                stmt.setObject(2, UUID.fromString(id))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun deleteExpired() {
        dataSource.connection.use { conn ->
            conn.prepareStatement(DELETE_EXPIRED_SQL).use { stmt ->
                stmt.setTimestamp(1, java.sql.Timestamp.from(Instant.now()))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }
}
