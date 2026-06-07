package profile.search

import profile.infrastructure.db.UserRepository
import profile.infrastructure.db.User
import profile.infrastructure.redis.RedisManager
import profile.users.UserPublicDto
import profile.users.toPublicDto

class SearchService(
    private val userRepository: UserRepository,
    private val redisManager: RedisManager
) {
    companion object {
        private const val MAX_SEARCH_LIMIT = 50
    }

    private val indexKey = "users:idx:username"

    fun indexUser(user: User) {
        val redis = redisManager.sync() ?: return
        val entry = "${user.username.lowercase()}:${user.id}"
        redis.zadd(indexKey, 0.0, entry)
    }

    fun reindexUser(previous: User, updated: User) {
        val redis = redisManager.sync() ?: return
        redis.zrem(indexKey, "${previous.username.lowercase()}:${previous.id}")
        redis.zadd(indexKey, 0.0, "${updated.username.lowercase()}:${updated.id}")
    }

    fun indexAllUsers() {
        val redis = redisManager.sync() ?: return
        val users = userRepository.findAll()
        users.forEach { indexUser(it) }
    }

    fun searchByUsernamePrefix(prefix: String, limit: Int = 10): List<UserPublicDto> {
        if (prefix.isBlank()) return emptyList()
        val redis = redisManager.sync() ?: return emptyList()
        val cappedLimit = minOf(limit, MAX_SEARCH_LIMIT)

        val results = redis.zrangebylex(
            indexKey, 
            io.lettuce.core.Range.from(
                io.lettuce.core.Range.Boundary.including(prefix.lowercase()), 
                io.lettuce.core.Range.Boundary.excluding(prefix.lowercase() + "\u00ff")
            ), 
            io.lettuce.core.Limit.create(0, cappedLimit.toLong())
        )

        val ids = results.map { it.split(":").last() }
        if (ids.isEmpty()) return emptyList()
        
        return userRepository.findByIds(ids).map { it.toPublicDto() }
    }
}
