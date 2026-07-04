package profile.users

import profile.infrastructure.db.User
import profile.infrastructure.db.UserRepository
import profile.infrastructure.db.PendingRegistrationStore
import profile.infrastructure.storage.S3Client
import profile.infrastructure.storage.AvatarProcessor
import profile.domain.SocialLink
import profile.search.SearchService
import profile.shared.ApiErrorCode
import profile.shared.apiError
import java.net.URI
import java.time.LocalDate

class UserService(
    private val userRepository: UserRepository,
    private val s3Client: S3Client,
    private val searchService: SearchService,
    private val pendingRegistrationStore: PendingRegistrationStore
) {
    fun getProfile(userId: String): User? {
        return userRepository.findById(userId)
    }

    fun getProfileByUsername(username: String): User? {
        return userRepository.findByUsername(username)
    }

    fun updateProfile(userId: String, request: UpdateProfileRequest): User {
        val user = userRepository.findById(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        val username = request.username?.trim() ?: user.username
        if (username.length < 3) apiError(ApiErrorCode.VALIDATION_USERNAME_TOO_SHORT, "username")
        val birthDate = nextBirthDate(user.birthDate, request)
        val socialLinks = nextSocialLinks(user.socialLinks, request)

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
            bio = request.bio ?: user.bio,
            birthDate = birthDate,
            socialLinks = socialLinks
        )

        val updated = userRepository.findById(userId)!!
        if (updated.username != user.username) searchService.reindexUser(user, updated)
        return updated
    }

    private fun nextBirthDate(current: String?, request: UpdateProfileRequest): String? {
        if (!request.birthDateProvided) return current
        val raw = request.birthDate?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val parsed = runCatching { LocalDate.parse(raw) }.getOrNull()
            ?: apiError(ApiErrorCode.VALIDATION_INVALID_REQUEST, "birthDate")
        if (parsed.isAfter(LocalDate.now())) apiError(ApiErrorCode.VALIDATION_INVALID_REQUEST, "birthDate")
        return parsed.toString()
    }

    private fun nextSocialLinks(current: List<SocialLink>, request: UpdateProfileRequest): List<SocialLink> {
        if (!request.socialLinksProvided) return current
        val normalized = (request.socialLinks ?: emptyList()).mapNotNull { link ->
            val label = link.label.trim()
            val url = link.url.trim()
            if (label.isBlank() && url.isBlank()) return@mapNotNull null
            if (label.isBlank() || url.isBlank() || label.length > 60 || url.length > 2048) {
                apiError(ApiErrorCode.VALIDATION_INVALID_REQUEST, "socialLinks")
            }
            val parsed = runCatching { URI(url) }.getOrNull()
                ?: apiError(ApiErrorCode.VALIDATION_INVALID_REQUEST, "socialLinks")
            if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) {
                apiError(ApiErrorCode.VALIDATION_INVALID_REQUEST, "socialLinks")
            }
            SocialLink(label = label, url = url)
        }
        if (normalized.size > 10) apiError(ApiErrorCode.VALIDATION_INVALID_REQUEST, "socialLinks")
        return normalized
    }

    suspend fun getAvatarStream(key: String): java.io.InputStream? {
        return s3Client.getObject(key)
    }

    suspend fun getAvatarSize(key: String): Long? {
        return s3Client.getObjectSize(key)
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
