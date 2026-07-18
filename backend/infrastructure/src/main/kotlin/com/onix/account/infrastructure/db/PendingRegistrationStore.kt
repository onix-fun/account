package com.onix.account.infrastructure.db

import com.onix.account.infrastructure.config.RegistrationConfig
import com.onix.account.domain.UuidV7
import com.onix.account.shared.ApiErrorCode
import com.onix.account.shared.apiError
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class PendingRegistration(
    val id: String = UuidV7.generate().toString(),
    val email: String,
    val username: String,
    val passwordHash: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val preferredLocale: String = "en",
    val codeHash: String,
    val codeAttempts: Int = 0,
    val codeCreatedAtEpochSeconds: Long = Instant.now().epochSecond,
    val createdAtEpochSeconds: Long = Instant.now().epochSecond
)

class PendingRegistrationStore(
    private val dataSource: DataSource,
    private val config: RegistrationConfig
) {
    val ttlSeconds: Long = config.pendingTtlSeconds

    fun create(pending: PendingRegistration) {
        dataSource.connection.use { conn ->
            try {
                pruneExpired(conn)
                conn.prepareStatement(CREATE_SQL).use {
                    bindPending(it, pending, Instant.ofEpochSecond(pending.createdAtEpochSeconds).plusSeconds(ttlSeconds))
                    it.executeUpdate()
                }
                conn.commit()
            } catch (error: SQLException) {
                conn.rollback()
                if (error.sqlState == UNIQUE_VIOLATION) {
                    val constraint = error.message.orEmpty()
                    if (constraint.contains("username", ignoreCase = true)) {
                        apiError(ApiErrorCode.AUTH_REGISTRATION_PENDING, "username")
                    }
                    apiError(ApiErrorCode.AUTH_REGISTRATION_PENDING, "email")
                }
                throw error
            }
        }
    }

    fun findByEmail(email: String): PendingRegistration? {
        dataSource.connection.use { conn ->
            val pending = conn.prepareStatement(FIND_BY_EMAIL_SQL).use {
                it.setString(1, email)
                val rs = it.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            return pending
        }
    }

    fun findByIdentifier(identifier: String): PendingRegistration? {
        val normalized = identifier.trim()
        if (normalized.contains("@")) return findByEmail(normalized.lowercase())
        dataSource.connection.use { conn ->
            val pending = conn.prepareStatement(FIND_BY_USERNAME_SQL).use {
                it.setString(1, normalized)
                val rs = it.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
            conn.commit()
            return pending
        }
    }

    fun findEmailByCodeHash(codeHash: String): String? {
        dataSource.connection.use { conn ->
            val email = conn.prepareStatement(FIND_EMAIL_BY_CODE_HASH_SQL).use {
                it.setString(1, codeHash)
                val rs = it.executeQuery()
                if (rs.next()) rs.getString("email") else null
            }
            conn.commit()
            return email
        }
    }

    fun isUsernameReserved(username: String): Boolean {
        dataSource.connection.use { conn ->
            val reserved = conn.prepareStatement(USERNAME_RESERVED_SQL).use {
                it.setString(1, username)
                val rs = it.executeQuery()
                rs.next() && rs.getBoolean(1)
            }
            conn.commit()
            return reserved
        }
    }

    fun refreshCode(email: String, codeHash: String): PendingRegistration {
        dataSource.connection.use { conn ->
            try {
                pruneExpired(conn)
                val existing = findByEmailForUpdate(conn, email)
                    ?: apiError(ApiErrorCode.AUTH_PENDING_REGISTRATION_NOT_FOUND, "email")
                if (existing.codeCreatedAtEpochSeconds > Instant.now().epochSecond - 60) {
                    apiError(ApiErrorCode.AUTH_CODE_RESEND_TOO_SOON)
                }

                val now = Instant.now()
                val updated = conn.prepareStatement(REFRESH_CODE_SQL).use {
                    it.setString(1, codeHash)
                    it.setTimestamp(2, Timestamp.from(now))
                    it.setTimestamp(3, Timestamp.from(now.plusSeconds(ttlSeconds)))
                    it.setString(4, email)
                    val rs = it.executeQuery()
                    if (rs.next()) mapRow(rs) else apiError(ApiErrorCode.AUTH_PENDING_REGISTRATION_NOT_FOUND, "email")
                }
                conn.commit()
                return updated
            } catch (error: SQLException) {
                conn.rollback()
                throw error
            }
        }
    }

    fun verifyCode(email: String, codeHash: String) {
        dataSource.connection.use { conn ->
            try {
                val pending = findByEmailForUpdate(conn, email)
                    ?: apiError(ApiErrorCode.AUTH_PENDING_REGISTRATION_NOT_FOUND, "identifier")
                if (pending.codeAttempts >= MAX_CODE_ATTEMPTS) apiError(ApiErrorCode.AUTH_CODE_LOCKED, "code")
                if (pending.codeHash == codeHash) {
                    conn.commit()
                    return
                }

                val attempts = pending.codeAttempts + 1
                conn.prepareStatement(INCREMENT_ATTEMPTS_SQL).use {
                    it.setInt(1, attempts)
                    it.setString(2, email)
                    it.executeUpdate()
                }
                conn.commit()
                if (attempts >= MAX_CODE_ATTEMPTS) apiError(ApiErrorCode.AUTH_CODE_LOCKED, "code")
                apiError(ApiErrorCode.AUTH_INVALID_OR_EXPIRED_CODE, "code")
            } catch (error: SQLException) {
                conn.rollback()
                throw error
            }
        }
    }

    fun delete(email: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(DELETE_SQL).use {
                it.setString(1, email)
                it.executeUpdate()
            }
            conn.commit()
        }
    }

    private fun findByEmailForUpdate(conn: Connection, email: String): PendingRegistration? {
        return conn.prepareStatement(FIND_BY_EMAIL_FOR_UPDATE_SQL).use {
            it.setString(1, email)
            val rs = it.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    private fun pruneExpired(conn: Connection) {
        conn.prepareStatement(PRUNE_EXPIRED_SQL).use { it.executeUpdate() }
    }

    private fun bindPending(stmt: java.sql.PreparedStatement, pending: PendingRegistration, expiresAt: Instant) {
        stmt.setString(1, pending.id)
        stmt.setString(2, pending.email)
        stmt.setString(3, pending.username)
        stmt.setString(4, pending.passwordHash)
        stmt.setString(5, pending.firstName)
        stmt.setString(6, pending.lastName)
        stmt.setString(7, pending.codeHash)
        stmt.setString(8, pending.preferredLocale)
        stmt.setInt(9, pending.codeAttempts)
        stmt.setTimestamp(10, Timestamp.from(Instant.ofEpochSecond(pending.codeCreatedAtEpochSeconds)))
        stmt.setTimestamp(11, Timestamp.from(expiresAt))
        stmt.setTimestamp(12, Timestamp.from(Instant.ofEpochSecond(pending.createdAtEpochSeconds)))
    }

    private fun mapRow(rs: ResultSet): PendingRegistration {
        return PendingRegistration(
            id = rs.getObject("id").toString(),
            email = rs.getString("email"),
            username = rs.getString("username"),
            passwordHash = rs.getString("password_hash"),
            firstName = rs.getString("first_name"),
            lastName = rs.getString("last_name"),
            preferredLocale = rs.getString("preferred_locale") ?: "en",
            codeHash = rs.getString("code_hash"),
            codeAttempts = rs.getInt("code_attempts"),
            codeCreatedAtEpochSeconds = rs.getTimestamp("code_created_at").toInstant().epochSecond,
            createdAtEpochSeconds = rs.getTimestamp("created_at").toInstant().epochSecond
        )
    }

    private companion object {
        private const val UNIQUE_VIOLATION = "23505"
        private const val MAX_CODE_ATTEMPTS = 5
        private const val CREATE_SQL = """
            INSERT INTO pending_registrations (
                id, email, username, password_hash, first_name, last_name,
                code_hash, preferred_locale, code_attempts, code_created_at, expires_at, created_at
            )
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        private const val FIND_BY_EMAIL_SQL = """
            SELECT * FROM pending_registrations
            WHERE LOWER(email) = LOWER(?) AND expires_at > CURRENT_TIMESTAMP
            LIMIT 1
        """
        private const val FIND_BY_EMAIL_FOR_UPDATE_SQL = """
            SELECT * FROM pending_registrations
            WHERE LOWER(email) = LOWER(?) AND expires_at > CURRENT_TIMESTAMP
            LIMIT 1
            FOR UPDATE
        """
        private const val FIND_BY_USERNAME_SQL = """
            SELECT * FROM pending_registrations
            WHERE LOWER(username) = LOWER(?) AND expires_at > CURRENT_TIMESTAMP
            LIMIT 1
        """
        private const val FIND_EMAIL_BY_CODE_HASH_SQL = """
            SELECT email FROM pending_registrations
            WHERE code_hash = ? AND expires_at > CURRENT_TIMESTAMP
            LIMIT 1
        """
        private const val USERNAME_RESERVED_SQL = """
            SELECT EXISTS(
                SELECT 1 FROM pending_registrations
                WHERE LOWER(username) = LOWER(?) AND expires_at > CURRENT_TIMESTAMP
            )
        """
        private const val REFRESH_CODE_SQL = """
            UPDATE pending_registrations
            SET code_hash = ?,
                code_attempts = 0,
                code_created_at = ?,
                expires_at = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE LOWER(email) = LOWER(?) AND expires_at > CURRENT_TIMESTAMP
            RETURNING *
        """
        private const val INCREMENT_ATTEMPTS_SQL = """
            UPDATE pending_registrations
            SET code_attempts = ?, updated_at = CURRENT_TIMESTAMP
            WHERE LOWER(email) = LOWER(?) AND expires_at > CURRENT_TIMESTAMP
        """
        private const val DELETE_SQL = "DELETE FROM pending_registrations WHERE LOWER(email) = LOWER(?)"
        private const val PRUNE_EXPIRED_SQL = "DELETE FROM pending_registrations WHERE expires_at <= CURRENT_TIMESTAMP"
    }
}
