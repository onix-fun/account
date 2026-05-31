package profile.users

import profile.auth.AuthService
import profile.infrastructure.db.User
import profile.infrastructure.db.UserRepository
import profile.infrastructure.storage.S3Client

class UserService(
    private val userRepository: UserRepository,
    private val authService: AuthService,
    private val s3Client: S3Client
) {
    fun getProfile(userId: String): User? {
        return userRepository.findById(userId)
    }

    fun updateProfile(userId: String, request: UpdateProfileRequest): User {
        val user = userRepository.findById(userId) ?: throw IllegalArgumentException("User not found")
        
        val newEmail = request.email ?: user.email
        var emailChanged = false

        if (newEmail != user.email) {
            val existing = userRepository.findByEmail(newEmail)
            if (existing != null) throw IllegalArgumentException("Email already in use")
            emailChanged = true
        }

        userRepository.updateProfile(
            userId = userId,
            email = newEmail,
            firstName = request.firstName ?: user.firstName,
            lastName = request.lastName ?: user.lastName,
            bio = request.bio ?: user.bio
        )

        if (emailChanged) {
            userRepository.updateEmailVerified(userId, false)
            authService.requestEmailVerification(userId)
        }

        return userRepository.findById(userId)!!
    }

    suspend fun updateAvatar(userId: String, bytes: ByteArray, contentType: String): User {
        userRepository.findById(userId) ?: throw IllegalArgumentException("User not found")
        val avatarUrl = s3Client.uploadAvatar(userId, bytes, contentType)
        
        userRepository.updateAvatar(userId, avatarUrl)
        
        return userRepository.findById(userId)!!
    }
}
