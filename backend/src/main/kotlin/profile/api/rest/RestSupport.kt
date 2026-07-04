package profile.api.rest

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

fun extractUserId(call: ApplicationCall): UUID? =
    call.principal<JWTPrincipal>()?.payload?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }

fun requireUserId(call: ApplicationCall): UUID =
    extractUserId(call) ?: throw UnauthorizedException()

class UnauthorizedException : RuntimeException("Authentication required")
