package profile.infrastructure.persistence

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import profile.infrastructure.db.User
import java.sql.Connection
import java.time.Instant
import java.util.*

object Users : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255)
    val username = varchar("username", 50)
    val passwordHash = varchar("password_hash", 255)
    val emailVerified = bool("email_verified")
    val firstName = varchar("first_name", 100).nullable()
    val lastName = varchar("last_name", 100).nullable()
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val bio = varchar("bio", 500).nullable()
    val role = varchar("role", 20)
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

class UserRepository(private val database: Database) {
    companion object {
        private const val CREATE_USER_SQL = """
            INSERT INTO users (id, email, username, password_hash, email_verified, first_name, last_name, role, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        private const val FIND_BY_EMAIL_SQL = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)"
        private const val FIND_BY_ID_SQL = "SELECT * FROM users WHERE id = ?"
        private const val FIND_BY_USERNAME_SQL = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)"
    }

    fun create(user: User) = transaction(database) {
        val conn = connection.connection as Connection
        conn.prepareStatement(CREATE_USER_SQL).use { stmt ->
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
    }

    fun updateProfile(userId: String, email: String, firstName: String?, lastName: String?, bio: String?) = transaction(database) {
        val conn = connection.connection as Connection
        val sql = "UPDATE users SET email = ?, first_name = ?, last_name = ?, bio = ?, updated_at = ? WHERE id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, email)
            stmt.setString(2, firstName)
            stmt.setString(3, lastName)
            stmt.setString(4, bio)
            stmt.setTimestamp(5, java.sql.Timestamp.from(Instant.now()))
            stmt.setObject(6, UUID.fromString(userId))
            stmt.executeUpdate()
        }
    }

    fun updateAvatar(userId: String, avatarUrl: String?) = transaction(database) {
        val conn = connection.connection as Connection
        val sql = "UPDATE users SET avatar_url = ?, updated_at = ? WHERE id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, avatarUrl)
            stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()))
            stmt.setObject(3, UUID.fromString(userId))
            stmt.executeUpdate()
        }
    }

    fun findByEmail(email: String): User? = transaction(database) {
        val conn = connection.connection as Connection
        conn.prepareStatement(FIND_BY_EMAIL_SQL).use { stmt ->
            stmt.setString(1, email)
            val rs = stmt.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    fun findById(id: String): User? = transaction(database) {
        val conn = connection.connection as Connection
        conn.prepareStatement(FIND_BY_ID_SQL).use { stmt ->
            stmt.setObject(1, UUID.fromString(id))
            val rs = stmt.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    fun findByUsername(username: String): User? = transaction(database) {
        val conn = connection.connection as Connection
        conn.prepareStatement(FIND_BY_USERNAME_SQL).use { stmt ->
            stmt.setString(1, username)
            val rs = stmt.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    fun updateEmailVerified(userId: String, verified: Boolean) = transaction(database) {
        val conn = connection.connection as Connection
        val sql = "UPDATE users SET email_verified = ?, updated_at = ? WHERE id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setBoolean(1, verified)
            stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()))
            stmt.setObject(3, UUID.fromString(userId))
            stmt.executeUpdate()
        }
    }

    fun updatePassword(userId: String, passwordHash: String) = transaction(database) {
        val conn = connection.connection as Connection
        val sql = "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, passwordHash)
            stmt.setTimestamp(2, java.sql.Timestamp.from(Instant.now()))
            stmt.setObject(3, UUID.fromString(userId))
            stmt.executeUpdate()
        }
    }

    fun findByIds(ids: List<String>): List<User> = transaction(database) {
        if (ids.isEmpty()) return@transaction emptyList()
        Users.selectAll().where { Users.id inList ids.map { UUID.fromString(it) } }
            .map { mapResultRow(it) }
    }

    fun findAll(): List<User> = transaction(database) {
        Users.selectAll().map { mapResultRow(it) }
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

    private fun mapResultRow(it: ResultRow): User {
        return User(
            id = it[Users.id].toString(),
            email = it[Users.email],
            username = it[Users.username],
            passwordHash = it[Users.passwordHash],
            emailVerified = it[Users.emailVerified],
            firstName = it[Users.firstName],
            lastName = it[Users.lastName],
            avatarUrl = it[Users.avatarUrl],
            bio = it[Users.bio],
            role = it[Users.role],
            status = it[Users.status],
            createdAt = it[Users.createdAt],
            updatedAt = it[Users.updatedAt]
        )
    }
}
