package com.onix.account.infrastructure.db

import com.onix.account.shared.ApiErrorCode
import com.onix.account.shared.apiError
import com.onix.account.domain.UuidV7
import java.time.Instant
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

data class VerificationToken(
    val id: String = UuidV7.generate().toString(),
    val userId: String,
    val tokenHash: String,
    val purpose: String,
    val expiresAt: Instant,
    val attempts: Int = 0,
    val maxAttempts: Int = 5,
    val createdAt: Instant = Instant.now()
)

class VerificationTokenRepository(private val dataSource: DataSource) {
    fun create(token: VerificationToken) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))").use {
                it.setString(1, "${token.userId}:${token.purpose}"); it.execute()
            }
            conn.prepareStatement("UPDATE verification_tokens SET consumed_at = CURRENT_TIMESTAMP WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL AND used_at IS NULL").use {
                it.setObject(1, UUID.fromString(token.userId)); it.setString(2, token.purpose); it.executeUpdate()
            }
            conn.prepareStatement("INSERT INTO verification_tokens (id,user_id,token_hash,purpose,expires_at,max_attempts,created_at) VALUES (?::uuid,?,?,?,?,?,?)").use {
                it.setString(1, token.id); it.setObject(2, UUID.fromString(token.userId)); it.setString(3, token.tokenHash)
                it.setString(4, token.purpose); it.setTimestamp(5, java.sql.Timestamp.from(token.expiresAt)); it.setInt(6, token.maxAttempts)
                it.setTimestamp(7, java.sql.Timestamp.from(token.createdAt)); it.executeUpdate()
            }
            conn.commit()
        }
    }

    fun latest(userId: String, purpose: String): VerificationToken? = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT * FROM verification_tokens WHERE user_id=? AND purpose=? AND consumed_at IS NULL AND used_at IS NULL ORDER BY created_at DESC LIMIT 1").use {
            it.setObject(1, UUID.fromString(userId)); it.setString(2, purpose)
            val rs = it.executeQuery(); if (rs.next()) mapRow(rs) else null
        }
    }

    fun verify(userId: String, purpose: String, hash: String): VerificationToken {
        dataSource.connection.use { conn ->
            try {
                val token = conn.prepareStatement("SELECT * FROM verification_tokens WHERE user_id=? AND purpose=? AND consumed_at IS NULL AND used_at IS NULL ORDER BY created_at DESC LIMIT 1 FOR UPDATE").use {
                    it.setObject(1, UUID.fromString(userId)); it.setString(2, purpose)
                    val rs = it.executeQuery(); if (rs.next()) mapRow(rs) else null
                } ?: apiError(ApiErrorCode.AUTH_INVALID_OR_EXPIRED_CODE, "code")
                if (token.expiresAt.isBefore(Instant.now())) {
                    consume(conn, token.id); conn.commit(); apiError(ApiErrorCode.AUTH_INVALID_OR_EXPIRED_CODE, "code")
                }
                if (token.attempts >= token.maxAttempts) apiError(ApiErrorCode.AUTH_CODE_LOCKED, "code")
                if (token.tokenHash != hash) {
                    val attempts = token.attempts + 1
                    conn.prepareStatement("UPDATE verification_tokens SET attempts=?, locked_at=CASE WHEN ? >= max_attempts THEN CURRENT_TIMESTAMP ELSE locked_at END WHERE id=?").use {
                        it.setInt(1, attempts); it.setInt(2, attempts); it.setObject(3, UUID.fromString(token.id)); it.executeUpdate()
                    }
                    conn.commit()
                    if (attempts >= token.maxAttempts) apiError(ApiErrorCode.AUTH_CODE_LOCKED, "code")
                    apiError(ApiErrorCode.AUTH_INVALID_OR_EXPIRED_CODE, "code")
                }
                consume(conn, token.id); conn.commit(); return token
            } catch (error: SQLException) {
                conn.rollback()
                if (error.sqlState == "40001") apiError(ApiErrorCode.AUTH_INVALID_OR_EXPIRED_CODE, "code")
                throw error
            }
        }
    }

    fun invalidateAll(userId: String, purpose: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE verification_tokens SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=? AND purpose=? AND consumed_at IS NULL").use {
                it.setObject(1, UUID.fromString(userId)); it.setString(2, purpose); it.executeUpdate()
            }; conn.commit()
        }
    }

    fun findByHash(hash: String): VerificationToken? = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT * FROM verification_tokens WHERE token_hash=? AND consumed_at IS NULL AND used_at IS NULL LIMIT 1").use {
            it.setString(1, hash); val rs = it.executeQuery(); if (rs.next()) mapRow(rs) else null
        }
    }

    fun markAsUsed(id: String) {
        dataSource.connection.use { conn -> consume(conn, id); conn.commit() }
    }

    private fun consume(conn: java.sql.Connection, id: String) {
        conn.prepareStatement("UPDATE verification_tokens SET used_at=CURRENT_TIMESTAMP, consumed_at=CURRENT_TIMESTAMP WHERE id=?").use {
            it.setObject(1, UUID.fromString(id)); it.executeUpdate()
        }
    }

    private fun mapRow(rs: java.sql.ResultSet) = VerificationToken(
        rs.getObject("id").toString(), rs.getObject("user_id").toString(), rs.getString("token_hash"), rs.getString("purpose"),
        rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempts"), rs.getInt("max_attempts"), rs.getTimestamp("created_at").toInstant()
    )
}
