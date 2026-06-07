package profile.users

import profile.infrastructure.db.User
import profile.infrastructure.db.UserRepository
import profile.infrastructure.storage.S3Client
import profile.infrastructure.storage.AvatarProcessor
import profile.infrastructure.redis.PendingRegistrationStore
import profile.search.SearchService
import profile.shared.ApiErrorCode
import profile.shared.apiError

class UserService(
    private val userRepository: UserRepository,
    private val s3Client: S3Client,
    private val searchService: SearchService,
    private val pendingRegistrationStore: PendingRegistrationStore
) {
    fun getProfile(userId: String): User? {
        return userRepository.findById(userId)
    }

    fun updateProfile(userId: String, request: UpdateProfileRequest): User {
        val user = userRepository.findById(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        val username = request.username?.trim() ?: user.username
        if (username.length < 3) apiError(ApiErrorCode.VALIDATION_USERNAME_TOO_SHORT, "username")

        val owner = userRepository.findByUsername(username)
        if (owner != null && owner.id != userId) apiError(ApiErrorCode.AUTH_USERNAME_IN_USE, "username")
        if (
            !username.equals(user.username, ignoreCase = true) &&
            pendingRegistrationStore.isUsernameReserved(username)
        ) {
            apiError(ApiErrorCode.AUTH_USERNAME_IN_USE, "username")
        }
        
        userRepository.updateProfile(
            userId = userId,
            username = username,
            firstName = request.firstName ?: user.firstName,
            lastName = request.lastName ?: user.lastName,
            bio = request.bio ?: user.bio
        )

        val updated = userRepository.findById(userId)!!
        if (updated.username != user.username) searchService.reindexUser(user, updated)
        return updated
    }

    suspend fun updateAvatar(userId: String, bytes: ByteArray, contentType: String): User {
        val user = userRepository.findById(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        val processed = AvatarProcessor.process(bytes)
        val avatarUrl = s3Client.uploadAvatar(userId, processed.bytes, processed.contentType)
        try {
            userRepository.updateAvatar(userId, avatarUrl)
        } catch (error: Throwable) {
            runCatching { s3Client.deleteByPublicUrl(avatarUrl) }
            throw error
        }
        runCatching { s3Client.deleteByPublicUrl(user.avatarUrl) }
        return userRepository.findById(userId)!!
    }
}
