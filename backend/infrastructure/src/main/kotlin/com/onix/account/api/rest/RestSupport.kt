package com.onix.account.api.rest

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import com.onix.account.domain.OwnerRef
import com.onix.account.domain.OwnerType
import java.util.UUID

fun extractUserId(call: ApplicationCall): UUID? =
    call.principal<JWTPrincipal>()?.payload?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }

fun requireUserId(call: ApplicationCall): UUID =
    extractUserId(call) ?: throw UnauthorizedException()

fun activeOwnerRef(call: ApplicationCall): OwnerRef {
    val principal = call.principal<JWTPrincipal>() ?: throw UnauthorizedException()
    val type = runCatching {
        OwnerType.valueOf(principal.payload.getClaim("owner_type").asString() ?: OwnerType.USER.name)
    }.getOrDefault(OwnerType.USER)
    val id = principal.payload.getClaim("owner_id").asString()
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: UUID.fromString(principal.payload.subject)
    return OwnerRef(type, id)
}

class UnauthorizedException : RuntimeException("Authentication required")
