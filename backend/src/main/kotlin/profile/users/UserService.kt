package profile.users

import profile.infrastructure.db.User
import profile.infrastructure.db.UserRepository
import profile.infrastructure.storage.S3Client
import profile.infrastructure.storage.AvatarProcessor
import profile.shared.ApiErrorCode
import profile.shared.apiError

class UserService(
    private val userRepository: UserRepository,
    private val s3Client: S3Client
) {
    fun getProfile(userId: String): User? {
        return userRepository.findById(userId)
    }

    fun updateProfile(userId: String, request: UpdateProfileRequest): User {
        val user = userRepository.findById(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        
        userRepository.updateProfile(
            userId = userId,
            firstName = request.firstName ?: user.firstName,
            lastName = request.lastName ?: user.lastName,
            bio = request.bio ?: user.bio
        )

        return userRepository.findById(userId)!!
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
