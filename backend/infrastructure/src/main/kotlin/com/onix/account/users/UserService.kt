package com.onix.account.users

import com.onix.account.infrastructure.db.User
import com.onix.account.infrastructure.db.UserRepository
import com.onix.account.infrastructure.db.PendingRegistrationStore
import com.onix.account.infrastructure.clients.MediaAvatarClient
import com.onix.account.infrastructure.clients.ProfileClient
import com.onix.account.domain.SocialLink
import com.onix.account.search.SearchService
import com.onix.account.shared.ApiErrorCode
import com.onix.account.shared.apiError
import java.net.URI
import java.time.LocalDate

class UserService(
    private val userRepository: UserRepository,
    private val profileClient: ProfileClient,
    private val mediaClient: MediaAvatarClient,
    private val searchService: SearchService,
    private val pendingRegistrationStore: PendingRegistrationStore
) {
    fun getProfile(userId: String): User? {
        return userRepository.findById(userId)?.withPublicProfile()
    }

    fun getProfileByUsername(username: String): User? {
        return userRepository.findByUsername(username)?.withPublicProfile()
    }

    fun updateProfile(userId: String, request: UpdateProfileRequest): User {
        val user = getProfile(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
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

        val identity = userRepository.findById(userId)!!
        if (request.bio != null || request.socialLinks != null) {
            profileClient.update(
                ownerType = "USER", ownerId = userId, username = identity.username,
                displayName = listOfNotNull(identity.firstName, identity.lastName).joinToString(" ").ifBlank { identity.username },
                bio = request.bio ?: user.bio.orEmpty(), socialLinks = socialLinks
            )
        }
        val updated = getProfile(userId)!!
        if (updated.username != user.username) searchService.reindexUser(user, updated)
        return updated
    }

    fun updatePreferredLocale(userId: String, locale: String): User {
        val normalized = if (locale.lowercase().startsWith("ru")) "ru" else "en"
        userRepository.updatePreferredLocale(userId, normalized)
        return getProfile(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
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

    suspend fun updateAvatar(userId: String, bytes: ByteArray, contentType: String): User {
        val user = getProfile(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        if (profileClient.get("USER", userId) == null) {
            profileClient.update("USER", userId, user.username,
                listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { user.username },
                user.bio.orEmpty(), user.socialLinks)
        }
        val assetId = mediaClient.upload("USER", userId, bytes, contentType)
        profileClient.setAvatar("USER", userId, assetId)
        return getProfile(userId)!!
    }

    private fun User.withPublicProfile(): User {
        val profile = runCatching { profileClient.get("USER", id) }.getOrNull() ?: return this
        return copy(
            username = profile.username.ifBlank { username },
            avatarUrl = mediaClient.resolve("USER", id, profile.avatarAssetId),
            bio = profile.bio,
            socialLinks = profile.socialLinks
        )
    }
}
