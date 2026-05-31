package profile.sessions

import profile.infrastructure.db.SessionRepository
import profile.infrastructure.db.UserRepository
import profile.users.toPublicDto

class SessionService(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository
) {
    fun getSessionsForUser(userId: String, currentSessionId: String?): List<SessionInfoDto> {
        val sessions = sessionRepository.findActiveByUserId(userId)
        
        return sessions.map { session ->
            val user = userRepository.findById(session.userId)
            SessionInfoDto(
                id = session.id,
                userId = session.userId,
                isCurrent = session.id == currentSessionId,
                deviceId = session.deviceId,
                userAgent = session.userAgent,
                ipAddress = session.ipAddress,
                lastUsedAt = session.lastUsedAt.toString(),
                expiresAt = session.expiresAt.toString(),
                createdAt = session.createdAt.toString(),
                user = user?.toPublicDto()
            )
        }.sortedByDescending { it.isCurrent }
    }

    fun revokeSession(userId: String, sessionId: String) {
        val session = sessionRepository.findById(sessionId) ?: throw IllegalArgumentException("Session not found")
        if (session.userId != userId) throw IllegalArgumentException("Unauthorized to revoke this session")
        sessionRepository.revoke(sessionId)
    }

    fun revokeAllSessions(userId: String) {
        sessionRepository.revokeAllForUser(userId)
    }
}
