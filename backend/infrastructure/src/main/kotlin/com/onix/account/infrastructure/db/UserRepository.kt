package com.onix.account.infrastructure.db

import kotlinx.serialization.Serializable
import com.onix.account.domain.SocialLink
import com.onix.account.domain.UuidV7
import com.onix.account.shared.InstantSerializer
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.*
import javax.sql.DataSource
import org.postgresql.util.PSQLException
import com.onix.account.shared.ApiErrorCode
import com.onix.account.shared.apiError
import com.onix.account.usecases.BirthdayOwner
import com.onix.account.usecases.BirthdayOwnerReader

@Serializable
data class User(
    val id: String = UuidV7.generate().toString(),
    val email: String,
    val username: String,
    val passwordHash: String,
    val emailVerified: Boolean = false,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val birthDate: String? = null,
    val socialLinks: List<SocialLink> = emptyList(),
    val preferredLocale: String = "en",
    val role: String = "USER",
    val status: String = "ACTIVE",
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now()
)

class UserRepository(private val dataSource: DataSource) : BirthdayOwnerReader {
    companion object {
        private const val CREATE_SQL = """
            INSERT INTO users (id, email, username, password_hash, email_verified, first_name, last_name, preferred_locale, role, status, created_at, updated_at)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """
        private const val UPDATE_PROFILE_SQL = """
            UPDATE users
            SET username = ?,
                first_name = ?,
                last_name = ?,
                birth_date = ?,
                updated_at = ?
            WHERE id = ?
        """
        private const val UPDATE_EMAIL_SQL = "UPDATE users SET email = ?, email_verified = TRUE, updated_at = ? WHERE id = ?"
        private const val UPDATE_LOCALE_SQL = "UPDATE users SET preferred_locale = ?, updated_at = ? WHERE id = ?"
        private const val FIND_BY_EMAIL_SQL = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)"
        private const val FIND_BY_ID_SQL = "SELECT * FROM users WHERE id = ?"
        private const val FIND_BY_USERNAME_SQL = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)"
        private const val UPDATE_EMAIL_VERIFIED_SQL = "UPDATE users SET email_verified = ?, updated_at = ? WHERE id = ?"
        private const val UPDATE_PASSWORD_SQL = "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?"
        private const val FIND_BY_IDS_SQL = "SELECT * FROM users WHERE id IN (%s)"
        private const val FIND_ALL_SQL = "SELECT * FROM users"
        private const val DELETE_SQL = "DELETE FROM users WHERE id = ?"
        private const val FIND_BIRTHDAYS_FOR_FOLLOWING_SQL = """
            SELECT u.*
            FROM users u
            JOIN account.subscriptions s ON s.subscribed_to_id = u.id
            WHERE s.subscriber_id = ?
              AND s.status = 'ACCEPTED'
              AND u.birth_date IS NOT NULL
              AND EXTRACT(MONTH FROM u.birth_date) = ?
              AND EXTRACT(DAY FROM u.birth_date) = ?
        """
    }

    fun create(user: User): User {
        dataSource.connection.use { conn ->
            try {
                val created = conn.prepareStatement(CREATE_SQL).use { stmt ->
                    stmt.setString(1, user.id)
                    stmt.setString(2, user.email)
                    stmt.setString(3, user.username)
                    stmt.setString(4, user.passwordHash)
                    stmt.setBoolean(5, user.emailVerified)
                    stmt.setString(6, user.firstName)
                    stmt.setString(7, user.lastName)
                    stmt.setString(8, user.preferredLocale)
                    stmt.setString(9, user.role)
                    stmt.setString(10, user.status)
                    stmt.setTimestamp(11, java.sql.Timestamp.from(user.createdAt))
                    stmt.setTimestamp(12, java.sql.Timestamp.from(user.updatedAt))
                    val rs = stmt.executeQuery()
                    rs.next()
                    mapRow(rs)
                }
                conn.commit()
                return created
            } catch (error: SQLException) {
                conn.rollback()
                throwUniqueConflict(error)
            }
        }
    }

    fun updateProfile(
        userId: String,
        username: String,
        firstName: String?,
        lastName: String?,
        bio: String?,
        birthDate: String?,
        socialLinks: List<SocialLink>
    ) {
        dataSource.connection.use { conn ->
            try {
                conn.prepareStatement(UPDATE_PROFILE_SQL).use { stmt ->
                    stmt.setString(1, username)
                    stmt.setString(2, firstName)
                    stmt.setString(3, lastName)
                    if (birthDate != null) stmt.setObject(4, java.time.LocalDate.parse(birthDate))
                    else stmt.setNull(4, java.sql.Types.DATE)
                    stmt.setTimestamp(5, java.sql.Timestamp.from(Instant.now()))
                    stmt.setObject(6, UUID.fromString(userId))
                    stmt.executeUpdate()
                }
                conn.commit()
            } catch (error: SQLException) {
                conn.rollback()
                val constraint = error.constraintName()
                if (
                    error.sqlState == "23505" &&
                    (constraint.contains("uq_users_username_lower") || constraint.contains("users_username_key"))
                ) {
                    apiError(ApiErrorCode.AUTH_USERNAME_IN_USE, "username")
                }
                throw error
            }
        }
    }

    fun updateEmail(userId: String, email: String) {
        dataSource.connection.use { conn ->
            try {
                conn.prepareStatement(UPDATE_EMAIL_SQL).use { stmt ->
                    stmt.setString(1, email)
                    stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()))
                    stmt.setObject(3, UUID.fromString(userId))
                    stmt.executeUpdate()
                }
                conn.commit()
            } catch (error: SQLException) {
                conn.rollback()
                throwUniqueConflict(error, emailField = "newEmail")
            }
        }
    }

    fun updatePreferredLocale(userId: String, locale: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPDATE_LOCALE_SQL).use { stmt ->
                stmt.setString(1, normalizeLocale(locale))
                stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()))
                stmt.setObject(3, UUID.fromString(userId))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun findByEmail(email: String): User? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_EMAIL_SQL).use { stmt ->
                stmt.setString(1, email)
                val rs = stmt.executeQuery()
                if (rs.next()) return mapRow(rs)
            }
        }
        return null
    }

    fun findById(id: String): User? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_ID_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(id))
                val rs = stmt.executeQuery()
                if (rs.next()) return mapRow(rs)
            }
        }
        return null
    }

    fun findByUsername(username: String): User? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_USERNAME_SQL).use { stmt ->
                stmt.setString(1, username)
                val rs = stmt.executeQuery()
                if (rs.next()) return mapRow(rs)
            }
        }
        return null
    }

    fun updateEmailVerified(userId: String, verified: Boolean) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPDATE_EMAIL_VERIFIED_SQL).use { stmt ->
                stmt.setBoolean(1, verified)
                stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()))
                stmt.setObject(3, UUID.fromString(userId))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun updatePassword(userId: String, passwordHash: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPDATE_PASSWORD_SQL).use { stmt ->
                stmt.setString(1, passwordHash)
                stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()))
                stmt.setObject(3, UUID.fromString(userId))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun delete(userId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(DELETE_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun findByIds(ids: List<String>): List<User> {
        if (ids.isEmpty()) return emptyList()
        dataSource.connection.use { conn ->
            val placeholders = ids.joinToString(",") { "?" }
            conn.prepareStatement(FIND_BY_IDS_SQL.format(placeholders)).use { stmt ->
                ids.forEachIndexed { i, id -> stmt.setObject(i + 1, UUID.fromString(id)) }
                val rs = stmt.executeQuery()
                val result = mutableListOf<User>()
                while (rs.next()) result.add(mapRow(rs))
                return result
            }
        }
    }

    fun findAll(): List<User> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_ALL_SQL).use { stmt ->
                val rs = stmt.executeQuery()
                val result = mutableListOf<User>()
                while (rs.next()) result.add(mapRow(rs))
                return result
            }
        }
    }

    override fun findBirthdaysForFollowing(userId: String, month: Int, day: Int): List<BirthdayOwner> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BIRTHDAYS_FOR_FOLLOWING_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                stmt.setInt(2, month)
                stmt.setInt(3, day)
                val rs = stmt.executeQuery()
                val result = mutableListOf<BirthdayOwner>()
                while (rs.next()) result.add(BirthdayOwner(rs.getString("id")))
                return result
            }
        }
    }

    private fun throwUniqueConflict(error: SQLException, emailField: String = "email"): Nothing {
        if (error.sqlState == "23505") {
            val constraint = error.constraintName()
            when {
                constraint.contains("uq_users_email_lower") || constraint.contains("users_email_key") ->
                    apiError(ApiErrorCode.AUTH_EMAIL_IN_USE, emailField)
                constraint.contains("uq_users_username_lower") || constraint.contains("users_username_key") ->
                    apiError(ApiErrorCode.AUTH_USERNAME_IN_USE, "username")
            }
        }
        throw error
    }

    private fun SQLException.constraintName(): String =
        (this as? PSQLException)?.serverErrorMessage?.constraint.orEmpty()

    private fun mapRow(rs: java.sql.ResultSet): User {
        return User(
            id = rs.getObject("id").toString(),
            email = rs.getString("email"),
            username = rs.getString("username"),
            passwordHash = rs.getString("password_hash"),
            emailVerified = rs.getBoolean("email_verified"),
            firstName = rs.getString("first_name"),
            lastName = rs.getString("last_name"),
            avatarUrl = null,
            bio = null,
            birthDate = rs.getDate("birth_date")?.toLocalDate()?.toString(),
            socialLinks = emptyList(),
            preferredLocale = rs.getString("preferred_locale") ?: "en",
            role = rs.getString("role"),
            status = rs.getString("status"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private fun normalizeLocale(locale: String): String {
        val normalized = locale.lowercase().trim()
        return if (normalized.startsWith("ru")) "ru" else "en"
    }
}
