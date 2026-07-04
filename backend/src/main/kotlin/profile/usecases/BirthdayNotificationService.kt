package profile.usecases

import profile.domain.ProfileVisibility
import java.time.LocalDate
import java.util.UUID
import profile.infrastructure.db.UserRepository

class BirthdayNotificationService(
    private val userRepository: UserRepository,
    private val socialUseCases: SocialUseCases,
    private val privacyRepository: PrivacyRepository,
    private val notificationUseCases: NotificationUseCases
) {
    fun generateFor(userId: String, today: LocalDate = LocalDate.now()): Int {
        val recipientId = runCatching { UUID.fromString(userId) }.getOrNull() ?: return 0
        var created = 0
        userRepository.findBirthdaysForFollowing(userId, today.monthValue, today.dayOfMonth).forEach { birthdayUser ->
            val birthdayUserId = UUID.fromString(birthdayUser.id)
            val privacy = privacyRepository.get(birthdayUserId)
            val relationship = socialUseCases.getRelationship(recipientId, birthdayUserId)
            val visible = ProfileVisibility.canView(
                ownerId = birthdayUserId,
                viewerId = recipientId,
                relationship = relationship,
                privacy = privacy,
                audience = privacy.fieldVisibility.birthday
            )
            if (!visible) return@forEach

            val notification = notificationUseCases.createBirthdayNotification(
                recipientId = recipientId,
                birthdayUserId = birthdayUserId,
                dateKey = today.toString()
            )
            if (notification != null) created += 1
        }
        return created
    }
}
