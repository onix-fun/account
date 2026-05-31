package profile.sessions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

class SessionController(private val sessionService: SessionService) {

    suspend fun getSessions(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val sessionId = call.principal<JWTPrincipal>()?.payload?.getClaim("sid")?.asString()
        val sessions = sessionService.getSessionsForUser(userId, sessionId)
        call.respond(HttpStatusCode.OK, sessions)
    }

    suspend fun revokeSession(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val sessionId = call.parameters["id"] ?: throw IllegalArgumentException("Missing session ID")
        
        sessionService.revokeSession(userId, sessionId)
        call.respond(HttpStatusCode.OK, mapOf("message" to "Session revoked"))
    }
}
