package profile.infrastructure.db

import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

data class PendingEmailChange(val userId: String, val newEmail: String, val expiresAt: Instant)

class PendingEmailChangeRepository(private val dataSource: DataSource) {
    fun upsert(change: PendingEmailChange) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO pending_email_changes(user_id,new_email,expires_at) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET new_email=EXCLUDED.new_email,created_at=CURRENT_TIMESTAMP,expires_at=EXCLUDED.expires_at").use {
                it.setObject(1, UUID.fromString(change.userId)); it.setString(2, change.newEmail); it.setTimestamp(3, java.sql.Timestamp.from(change.expiresAt)); it.executeUpdate()
            }; conn.commit()
        }
    }
    fun find(userId: String): PendingEmailChange? = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT * FROM pending_email_changes WHERE user_id=? AND expires_at>CURRENT_TIMESTAMP").use {
            it.setObject(1, UUID.fromString(userId)); val rs=it.executeQuery()
            if (rs.next()) PendingEmailChange(userId, rs.getString("new_email"), rs.getTimestamp("expires_at").toInstant()) else null
        }
    }
    fun delete(userId: String) { dataSource.connection.use { conn -> conn.prepareStatement("DELETE FROM pending_email_changes WHERE user_id=?").use { it.setObject(1, UUID.fromString(userId)); it.executeUpdate() }; conn.commit() } }
}
