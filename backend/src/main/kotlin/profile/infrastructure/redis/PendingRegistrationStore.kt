package profile.infrastructure.redis

import kotlinx.serialization.Serializable
import profile.shared.ApiErrorCode
import profile.shared.apiError
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import profile.infrastructure.config.RegistrationConfig
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class PendingRegistration(
    val email: String,
    val username: String,
    val passwordHash: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val codeHash: String,
    val codeAttempts: Int = 0,
    val codeCreatedAtEpochSeconds: Long = Instant.now().epochSecond,
    val createdAtEpochSeconds: Long = Instant.now().epochSecond
)

class PendingRegistrationStore(
    private val redisManager: RedisManager,
    private val config: RegistrationConfig
) {
    private data class MemoryEntry(val value: String, val expiresAtMillis: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val pendingByEmail = ConcurrentHashMap<String, MemoryEntry>()
    private val emailByCodeHash = ConcurrentHashMap<String, MemoryEntry>()
    private val emailByUsername = ConcurrentHashMap<String, MemoryEntry>()

    val ttlSeconds: Long = config.pendingTtlSeconds

    fun create(pending: PendingRegistration) {
        val redis = redisManager.sync()
        if (redis != null) {
            val pendingKey = pendingKey(pending.email)
            val usernameKey = usernameKey(pending.username)
            if (redis.exists(pendingKey) > 0) apiError(ApiErrorCode.AUTH_REGISTRATION_PENDING, "email")
            if (redis.exists(usernameKey) > 0) apiError(ApiErrorCode.AUTH_REGISTRATION_PENDING, "username")

            redis.setex(pendingKey, ttlSeconds, json.encodeToString(pending))
            redis.setex(codeKey(pending.codeHash), ttlSeconds, pending.email)
            redis.setex(usernameKey, ttlSeconds, pending.email)
            return
        }

        if (!config.allowInMemoryFallback) {
            throw IllegalStateException("Redis is required for pending registration")
        }

        pruneMemory()
        if (pendingByEmail.containsKey(pending.email)) apiError(ApiErrorCode.AUTH_REGISTRATION_PENDING, "email")
        if (emailByUsername.containsKey(pending.username)) apiError(ApiErrorCode.AUTH_REGISTRATION_PENDING, "username")
        writeMemory(pending)
    }

    fun findByEmail(email: String): PendingRegistration? {
        val redis = redisManager.sync()
        if (redis != null) return redis.get(pendingKey(email))?.let { json.decodeFromString<PendingRegistration>(it) }

        if (!config.allowInMemoryFallback) return null
        pruneMemory()
        return pendingByEmail[email]?.value?.let { json.decodeFromString<PendingRegistration>(it) }
    }

    fun findByIdentifier(identifier: String): PendingRegistration? {
        val normalized = identifier.trim()
        if (normalized.contains("@")) return findByEmail(normalized.lowercase())
        val redis = redisManager.sync()
        if (redis != null) {
            val email = redis.get(usernameKey(normalized)) ?: return null
            return findByEmail(email)
        }
        if (!config.allowInMemoryFallback) return null
        pruneMemory()
        val email = emailByUsername[normalized]?.value ?: return null
        return findByEmail(email)
    }

    fun findEmailByCodeHash(codeHash: String): String? {
        val redis = redisManager.sync()
        if (redis != null) return redis.get(codeKey(codeHash))

        if (!config.allowInMemoryFallback) return null
        pruneMemory()
        return emailByCodeHash[codeHash]?.value
    }

    fun isUsernameReserved(username: String): Boolean {
        val redis = redisManager.sync()
        if (redis != null) return redis.exists(usernameKey(username)) > 0

        if (!config.allowInMemoryFallback) return false
        pruneMemory()
        return emailByUsername.containsKey(username)
    }

    fun refreshCode(email: String, codeHash: String): PendingRegistration {
        val existing = findByEmail(email) ?: apiError(ApiErrorCode.AUTH_PENDING_REGISTRATION_NOT_FOUND, "email")
        if (existing.codeCreatedAtEpochSeconds > Instant.now().epochSecond - 60) apiError(ApiErrorCode.AUTH_CODE_RESEND_TOO_SOON)
        val updated = existing.copy(codeHash = codeHash, codeAttempts = 0, codeCreatedAtEpochSeconds = Instant.now().epochSecond)

        val redis = redisManager.sync()
        if (redis != null) {
            redis.del(codeKey(existing.codeHash))
            redis.setex(pendingKey(email), ttlSeconds, json.encodeToString(updated))
            redis.setex(codeKey(codeHash), ttlSeconds, email)
            redis.setex(usernameKey(updated.username), ttlSeconds, email)
            return updated
        }

        if (!config.allowInMemoryFallback) {
            throw IllegalStateException("Redis is required for pending registration")
        }

        emailByCodeHash.remove(existing.codeHash)
        writeMemory(updated)
        return updated
    }

    fun verifyCode(email: String, codeHash: String) {
        val pending = findByEmail(email) ?: apiError(ApiErrorCode.AUTH_PENDING_REGISTRATION_NOT_FOUND, "identifier")
        if (pending.codeAttempts >= 5) apiError(ApiErrorCode.AUTH_CODE_LOCKED, "code")
        if (pending.codeHash == codeHash) return
        val updated = pending.copy(codeAttempts = pending.codeAttempts + 1)
        val redis = redisManager.sync()
        if (redis != null) redis.setex(pendingKey(email), ttlSeconds, json.encodeToString(updated)) else writeMemory(updated)
        if (updated.codeAttempts >= 5) apiError(ApiErrorCode.AUTH_CODE_LOCKED, "code")
        apiError(ApiErrorCode.AUTH_INVALID_OR_EXPIRED_CODE, "code")
    }

    fun delete(email: String) {
        val existing = findByEmail(email)
        val redis = redisManager.sync()
        if (redis != null) {
            redis.del(pendingKey(email))
            existing?.let {
                redis.del(codeKey(it.codeHash))
                redis.del(usernameKey(it.username))
            }
            return
        }

        if (!config.allowInMemoryFallback) return
        pendingByEmail.remove(email)
        existing?.let {
            emailByCodeHash.remove(it.codeHash)
            emailByUsername.remove(it.username)
        }
    }

    private fun writeMemory(pending: PendingRegistration) {
        val expiresAtMillis = System.currentTimeMillis() + ttlSeconds * 1000
        pendingByEmail[pending.email] = MemoryEntry(json.encodeToString(pending), expiresAtMillis)
        emailByCodeHash[pending.codeHash] = MemoryEntry(pending.email, expiresAtMillis)
        emailByUsername[pending.username] = MemoryEntry(pending.email, expiresAtMillis)
    }

    private fun pruneMemory() {
        val now = System.currentTimeMillis()
        pendingByEmail.entries.removeIf { it.value.expiresAtMillis <= now }
        emailByCodeHash.entries.removeIf { it.value.expiresAtMillis <= now }
        emailByUsername.entries.removeIf { it.value.expiresAtMillis <= now }
    }

    private fun pendingKey(email: String) = "profile:pending-registration:$email"
    private fun codeKey(codeHash: String) = "profile:registration-code:$codeHash"
    private fun usernameKey(username: String) = "profile:pending-registration-username:$username"
}
