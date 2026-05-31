package profile.infrastructure.db

import kotlinx.serialization.Serializable
import profile.shared.InstantSerializer
import java.sql.Connection
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@Serializable
data class User(
    val id: String,
    val email: String,
    val username: String,
    val passwordHash: String,
    val emailVerified: Boolean = false,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val role: String = "USER",
    val status: String = "ACTIVE",
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now()
)

class UserRepository(private val dataSource: DataSource) {
    companion object {
        private const val CREATE_SQL = """
            INSERT INTO users (id, email, username, password_hash, email_verified, first_name, last_name, role, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        private const val UPDATE_PROFILE_SQL = "UPDATE users SET email = ?, first_name = ?, last_name = ?, bio = ?, updated_at = ? WHERE id = ?"
        private const val UPDATE_AVATAR_SQL = "UPDATE users SET avatar_url = ?, updated_at = ? WHERE id = ?"
        private const val FIND_BY_EMAIL_SQL = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)"
        private const val FIND_BY_ID_SQL = "SELECT * FROM users WHERE id = ?"
        private const val FIND_BY_USERNAME_SQL = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)"
        private const val UPDATE_EMAIL_VERIFIED_SQL = "UPDATE users SET email_verified = ?, updated_at = ? WHERE id = ?"
        private const val UPDATE_PASSWORD_SQL = "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?"
        private const val FIND_BY_IDS_SQL = "SELECT * FROM users WHERE id IN (%s)"
        private const val FIND_ALL_SQL = "SELECT * FROM users"
    }

    fun create(user: User) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(CREATE_SQL).use { stmt ->
                stmt.setObject(1, UUID.fromString(user.id))
                stmt.setString(2, user.email)
                stmt.setString(3, user.username)
                stmt.setString(4, user.passwordHash)
                stmt.setBoolean(5, user.emailVerified)
                stmt.setString(6, user.firstName)
                stmt.setString(7, user.lastName)
                stmt.setString(8, user.role)
                stmt.setString(9, user.status)
                stmt.setTimestamp(10, java.sql.Timestamp.from(user.createdAt))
                stmt.setTimestamp(11, java.sql.Timestamp.from(user.updatedAt))
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun updateProfile(userId: String, email: String, firstName: String?, lastName: String?, bio: String?) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPDATE_PROFILE_SQL).use { stmt ->
                stmt.setString(1, email)
                stmt.setString(2, firstName)
                stmt.setString(3, lastName)
                stmt.setString(4, bio)
                stmt.setTimestamp(5, java.sql.Timestamp.from(Instant.now()))
                stmt.setObject(6, UUID.fromString(userId))
                stmt.executeUpdate()
            }
            conn.commit()
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
            role = rs.getString("role"),
            status = rs.getString("status"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }
}
