package profile.infrastructure.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import profile.domain.SocialLink
import profile.domain.UserProfileMetadata
import profile.shared.InstantSerializer
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.*
import javax.sql.DataSource
import org.postgresql.util.PSQLException
import profile.shared.ApiErrorCode
import profile.shared.apiError

@Serializable
data class User(
    val id: String = "",
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
    val role: String = "USER",
    val status: String = "ACTIVE",
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now()
)

class UserRepository(private val dataSource: DataSource) {
    companion object {
        private val metadataJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private const val CREATE_SQL = """
            INSERT INTO users (email, username, password_hash, email_verified, first_name, last_name, role, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        """
        private const val UPDATE_PROFILE_SQL = """
            UPDATE users
            SET username = ?,
                first_name = ?,
                last_name = ?,
                bio = ?,
                birth_date = ?,
                profile_metadata = ?::jsonb,
                updated_at = ?
            WHERE id = ?
        """
        private const val UPDATE_EMAIL_SQL = "UPDATE users SET email = ?, email_verified = TRUE, updated_at = ? WHERE id = ?"
        private const val UPDATE_AVATAR_SQL = "UPDATE users SET avatar_url = ?, updated_at = ? WHERE id = ?"
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
            JOIN social.subscriptions s ON s.subscribed_to_id = u.id
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
                    stmt.setString(1, user.email)
                    stmt.setString(2, user.username)
                    stmt.setString(3, user.passwordHash)
                    stmt.setBoolean(4, user.emailVerified)
                    stmt.setString(5, user.firstName)
                    stmt.setString(6, user.lastName)
                    stmt.setString(7, user.role)
                    stmt.setString(8, user.status)
                    stmt.setTimestamp(9, java.sql.Timestamp.from(user.createdAt))
                    stmt.setTimestamp(10, java.sql.Timestamp.from(user.updatedAt))
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
                    stmt.setString(4, bio)
                    if (birthDate != null) stmt.setObject(5, java.time.LocalDate.parse(birthDate))
                    else stmt.setNull(5, java.sql.Types.DATE)
                    stmt.setString(6, metadataJson.encodeToString(UserProfileMetadata(socialLinks = socialLinks)))
                    stmt.setTimestamp(7, java.sql.Timestamp.from(Instant.now()))
                    stmt.setObject(8, UUID.fromString(userId))
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

    fun updateAvatar(userId: String, avatarUrl: String?) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPDATE_AVATAR_SQL).use { stmt ->
                stmt.setString(1, avatarUrl)
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

    fun findBirthdaysForFollowing(userId: String, month: Int, day: Int): List<User> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BIRTHDAYS_FOR_FOLLOWING_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(userId))
                stmt.setInt(2, month)
                stmt.setInt(3, day)
                val rs = stmt.executeQuery()
                val result = mutableListOf<User>()
                while (rs.next()) result.add(mapRow(rs))
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
            avatarUrl = rs.getString("avatar_url"),
            bio = rs.getString("bio"),
            birthDate = rs.getDate("birth_date")?.toLocalDate()?.toString(),
            socialLinks = parseMetadata(rs.getString("profile_metadata")).socialLinks,
            role = rs.getString("role"),
            status = rs.getString("status"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private fun parseMetadata(raw: String?): UserProfileMetadata {
        if (raw.isNullOrBlank()) return UserProfileMetadata()
        return runCatching { metadataJson.decodeFromString<UserProfileMetadata>(raw) }
            .getOrDefault(UserProfileMetadata())
    }
}
