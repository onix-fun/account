package profile.infrastructure.persistence

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.time.Instant
import java.util.*

object VerificationTokens : Table("verification_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val tokenHash = varchar("token_hash", 255).uniqueIndex()
    val purpose = varchar("purpose", 50) // EMAIL_VERIFICATION, PASSWORD_RESET
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

data class VerificationToken(
    val id: String,
    val userId: String,
    val tokenHash: String,
    val purpose: String,
    val expiresAt: Instant,
    val usedAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

class VerificationTokenRepository(private val database: Database) {
    
    fun create(token: VerificationToken) = transaction(database) {
        val conn = connection.connection as Connection
        val sql = "INSERT INTO verification_tokens (id, user_id, token_hash, purpose, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, UUID.fromString(token.id))
            stmt.setObject(2, UUID.fromString(token.userId))
            stmt.setString(3, token.tokenHash)
            stmt.setString(4, token.purpose)
            stmt.setTimestamp(5, java.sql.Timestamp.from(token.expiresAt))
            stmt.setTimestamp(6, java.sql.Timestamp.from(token.createdAt))
            stmt.executeUpdate()
        }
    }

    fun findByHash(hash: String): VerificationToken? = transaction(database) {
        val conn = connection.connection as Connection
        val sql = "SELECT * FROM verification_tokens WHERE token_hash = ? AND used_at IS NULL"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, hash)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                VerificationToken(
                    id = rs.getObject("id").toString(),
                    userId = rs.getObject("user_id").toString(),
                    tokenHash = rs.getString("token_hash"),
                    purpose = rs.getString("purpose"),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                    usedAt = rs.getTimestamp("used_at")?.toInstant(),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            } else null
        }
    }

    fun markAsUsed(id: String) = transaction(database) {
        val conn = connection.connection as Connection
        val sql = "UPDATE verification_tokens SET used_at = ? WHERE id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setTimestamp(1, java.sql.Timestamp.from(Instant.now()))
            stmt.setObject(2, UUID.fromString(id))
            stmt.executeUpdate()
        }
    }

    fun deleteExpired() = transaction(database) {
        val conn = connection.connection as Connection
        val sql = "DELETE FROM verification_tokens WHERE expires_at < ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setTimestamp(1, java.sql.Timestamp.from(Instant.now()))
            stmt.executeUpdate()
        }
    }
}
