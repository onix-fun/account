package com.onix.account.sessions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import com.onix.account.shared.ApiErrorCode
import com.onix.account.shared.apiError

class SessionController(private val sessionService: SessionService) {

    suspend fun getSessions(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val sessionId = call.principal<JWTPrincipal>()?.payload?.getClaim("sid")?.asString()
        val sessions = sessionService.getSessionsForUser(userId, sessionId)
        call.respond(HttpStatusCode.OK, sessions)
    }

    suspend fun revokeSession(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val sessionId = call.parameters["id"] ?: apiError(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "id")
        
        sessionService.revokeSession(userId, sessionId)
        call.respond(HttpStatusCode.OK, mapOf("message" to "Session revoked"))
    }
}
