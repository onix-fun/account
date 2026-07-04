package profile.infrastructure.db

import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

data class QrLoginChallenge(
    val id: String,
    val userId: String,
    val scanTokenHash: String,
    val manualCodeHash: String,
    val status: String,
    val attempts: Int,
    val expiresAt: Instant,
    val consumedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

class QrLoginChallengeRepository(private val dataSource: DataSource) {
    fun create(userId: String, scanTokenHash: String, manualCodeHash: String, expiresAt: Instant): QrLoginChallenge {
        dataSource.connection.use { conn ->
            val challenge = conn.prepareStatement(CREATE_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                stmt.setString(2, scanTokenHash)
                stmt.setString(3, manualCodeHash)
                stmt.setTimestamp(4, java.sql.Timestamp.from(expiresAt))
                val rs = stmt.executeQuery()
                rs.next()
                mapRow(rs)
            }
            conn.commit()
            return challenge
        }
    }

    fun findForUser(id: String, userId: String): QrLoginChallenge? {
        val challengeId = parseUuid(id) ?: return null
        val ownerId = parseUuid(userId) ?: return null
        dataSource.connection.use { conn ->
            val challenge = conn.prepareStatement(FIND_FOR_USER_SQL).use { stmt ->
                stmt.setObject(1, challengeId)
                stmt.setObject(2, ownerId)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            return challenge
        }
    }

    fun cancel(id: String, userId: String): QrLoginChallenge? {
        val challengeId = parseUuid(id) ?: return null
        val ownerId = parseUuid(userId) ?: return null
        dataSource.connection.use { conn ->
            val challenge = conn.prepareStatement(CANCEL_SQL).use { stmt ->
                stmt.setObject(1, challengeId)
                stmt.setObject(2, ownerId)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            } ?: conn.prepareStatement(FIND_FOR_USER_SQL).use { stmt ->
                stmt.setObject(1, challengeId)
                stmt.setObject(2, ownerId)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            return challenge
        }
    }

    fun consumeByScanTokenHash(
        scanTokenHash: String,
        deviceId: String?,
        userAgent: String?,
        ipAddress: String?
    ): ConsumeResult {
        return consume(CONSUME_BY_SCAN_SQL, scanTokenHash, deviceId, userAgent, ipAddress) {
            findByScanTokenHash(scanTokenHash)
        }
    }

    fun consumeByManualCodeHash(
        manualCodeHash: String,
        deviceId: String?,
        userAgent: String?,
        ipAddress: String?
    ): ConsumeResult {
        return consume(CONSUME_BY_MANUAL_SQL, manualCodeHash, deviceId, userAgent, ipAddress) {
            findByManualCodeHash(manualCodeHash)
        }
    }

    private fun consume(
        sql: String,
        hash: String,
        deviceId: String?,
        userAgent: String?,
        ipAddress: String?,
        fallback: () -> QrLoginChallenge?
    ): ConsumeResult {
        dataSource.connection.use { conn ->
            val consumed = conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, deviceId)
                stmt.setString(2, userAgent)
                stmt.setString(3, ipAddress)
                stmt.setString(4, hash)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            if (consumed != null) return ConsumeResult.Consumed(consumed)
        }
        return ConsumeResult.NotConsumed(fallback())
    }

    private fun findByScanTokenHash(hash: String): QrLoginChallenge? = findByHash(FIND_BY_SCAN_HASH_SQL, hash)

    private fun findByManualCodeHash(hash: String): QrLoginChallenge? = findByHash(FIND_BY_MANUAL_HASH_SQL, hash)

    private fun findByHash(sql: String, hash: String): QrLoginChallenge? {
        dataSource.connection.use { conn ->
            val challenge = conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, hash)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            return challenge
        }
    }

    private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()

    private fun mapRow(rs: ResultSet): QrLoginChallenge {
        return QrLoginChallenge(
            id = rs.getObject("id").toString(),
            userId = rs.getObject("user_id").toString(),
            scanTokenHash = rs.getString("scan_token_hash"),
            manualCodeHash = rs.getString("manual_code_hash"),
            status = rs.getString("status"),
            attempts = rs.getInt("attempts"),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            consumedAt = rs.getTimestamp("consumed_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    sealed class ConsumeResult {
        data class Consumed(val challenge: QrLoginChallenge) : ConsumeResult()
        data class NotConsumed(val challenge: QrLoginChallenge?) : ConsumeResult()
    }

    private companion object {
        private const val CREATE_SQL = """
            INSERT INTO qr_login_challenges (user_id, scan_token_hash, manual_code_hash, expires_at)
            VALUES (?, ?, ?, ?)
            RETURNING *
        """
        private const val FIND_FOR_USER_SQL = """
            SELECT * FROM qr_login_challenges
            WHERE id = ? AND user_id = ?
        """
        private const val CANCEL_SQL = """
            UPDATE qr_login_challenges
            SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND user_id = ? AND status = 'PENDING'
            RETURNING *
        """
        private const val CONSUME_BY_SCAN_SQL = """
            UPDATE qr_login_challenges
            SET status = 'CONSUMED',
                consumed_at = CURRENT_TIMESTAMP,
                consumer_device_id = ?,
                consumer_user_agent = ?,
                consumer_ip_address = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE scan_token_hash = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP
            RETURNING *
        """
        private const val CONSUME_BY_MANUAL_SQL = """
            UPDATE qr_login_challenges
            SET status = 'CONSUMED',
                consumed_at = CURRENT_TIMESTAMP,
                consumer_device_id = ?,
                consumer_user_agent = ?,
                consumer_ip_address = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE manual_code_hash = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP
            RETURNING *
        """
        private const val FIND_BY_SCAN_HASH_SQL = "SELECT * FROM qr_login_challenges WHERE scan_token_hash = ?"
        private const val FIND_BY_MANUAL_HASH_SQL = "SELECT * FROM qr_login_challenges WHERE manual_code_hash = ?"
    }
}
