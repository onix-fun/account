package com.onix.account.organizations

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.*
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.onix.account.domain.SocialLink
import com.onix.account.domain.OwnerRef
import com.onix.account.domain.OwnerType
import com.onix.account.shared.ApiErrorCode
import com.onix.account.shared.apiError
import java.io.ByteArrayOutputStream
import java.util.UUID

class OrganizationController(private val organizationService: OrganizationService) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun context(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()!!
        call.respond(HttpStatusCode.OK, organizationService.context(principal.payload.subject, activeOwner(principal)))
    }

    suspend fun listMine(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, organizationService.listMine(userId(call)))
    }

    suspend fun create(call: ApplicationCall) {
        call.respond(HttpStatusCode.Created, organizationService.create(userId(call), call.receive()))
    }

    suspend fun update(call: ApplicationCall) {
        val body = call.receive<JsonObject>()
        val request = UpdateOrganizationRequest(
            orgName = body["orgName"]?.jsonPrimitive?.contentOrNull,
            displayName = body["displayName"]?.jsonPrimitive?.contentOrNull,
            bio = body["bio"]?.jsonPrimitive?.contentOrNull,
            socialLinks = body["socialLinks"]?.let { json.decodeFromString<List<SocialLink>>(it.toString()) },
            bioProvided = body.containsKey("bio")
        )
        call.respond(HttpStatusCode.OK, organizationService.update(userId(call), requireOrgId(call), request))
    }

    suspend fun uploadAvatar(call: ApplicationCall) {
        val multipart = call.receiveMultipart()
        var organization: com.onix.account.domain.OrganizationDto? = null

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val contentType = part.contentType?.withoutParameters()?.toString()?.lowercase()
                    ?: apiError(ApiErrorCode.AVATAR_CONTENT_TYPE_REQUIRED, "file")
                if (contentType !in ALLOWED_AVATAR_TYPES) {
                    apiError(ApiErrorCode.AVATAR_UNSUPPORTED_TYPE, "file")
                }
                val bytes = part.provider().readLimited(MAX_AVATAR_BYTES)
                validateAvatarSignature(bytes, contentType)
                organization = organizationService.updateAvatar(userId(call), requireOrgId(call), bytes, contentType)
            }
            part.dispose()
        }

        if (organization != null) call.respond(HttpStatusCode.OK, organization!!)
        else apiError(ApiErrorCode.AVATAR_FILE_REQUIRED, "file")
    }

    suspend fun getByName(call: ApplicationCall) {
        val name = call.parameters["orgName"] ?: apiError(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "orgName")
        val org = organizationService.findByName(name) ?: apiError(ApiErrorCode.USER_NOT_FOUND)
        call.respond(HttpStatusCode.OK, org)
    }

    suspend fun invite(call: ApplicationCall) {
        call.respond(HttpStatusCode.Created, organizationService.invite(userId(call), requireOrgId(call), call.receive()))
    }

    suspend fun invitations(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, organizationService.pendingInvitations(userId(call)))
    }

    suspend fun acceptInvitation(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, organizationService.acceptInvitation(userId(call), requireInvitationId(call)))
    }

    suspend fun declineInvitation(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, organizationService.declineInvitation(userId(call), requireInvitationId(call)))
    }

    suspend fun members(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, organizationService.members(userId(call), requireOrgId(call)))
    }

    suspend fun updateMember(call: ApplicationCall) {
        val request = call.receive<UpdateOrganizationMemberRequest>()
        organizationService.updateMember(userId(call), requireOrgId(call), requireMemberUserId(call), request.role)
        call.respond(HttpStatusCode.OK)
    }

    suspend fun removeMember(call: ApplicationCall) {
        organizationService.removeMember(userId(call), requireOrgId(call), requireMemberUserId(call))
        call.respond(HttpStatusCode.NoContent)
    }

    private fun activeOwner(principal: JWTPrincipal): OwnerRef {
        val type = runCatching {
            OwnerType.valueOf(principal.payload.getClaim("owner_type").asString() ?: OwnerType.USER.name)
        }.getOrDefault(OwnerType.USER)
        val id = principal.payload.getClaim("owner_id").asString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.fromString(principal.payload.subject)
        return OwnerRef(type, id)
    }

    private fun userId(call: ApplicationCall): String = call.principal<JWTPrincipal>()!!.payload.subject

    private fun requireOrgId(call: ApplicationCall): String =
        call.parameters["orgId"] ?: apiError(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "orgId")

    private fun requireInvitationId(call: ApplicationCall): String =
        call.parameters["invitationId"] ?: apiError(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "invitationId")

    private fun requireMemberUserId(call: ApplicationCall): String =
        call.parameters["userId"] ?: apiError(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "userId")

    private suspend fun ByteReadChannel.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            total += read
            if (total > maxBytes) apiError(ApiErrorCode.AVATAR_TOO_LARGE, "file")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun validateAvatarSignature(bytes: ByteArray, contentType: String) {
        val valid = when (contentType) {
            "image/jpeg" -> bytes.size >= 3 &&
                bytes[0] == 0xff.toByte() &&
                bytes[1] == 0xd8.toByte() &&
                bytes[2] == 0xff.toByte()
            "image/png" -> bytes.size >= PNG_SIGNATURE.size &&
                bytes.take(PNG_SIGNATURE.size).toByteArray().contentEquals(PNG_SIGNATURE)
            "image/webp" -> bytes.size >= 12 &&
                bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
            else -> false
        }
        if (!valid) apiError(ApiErrorCode.AVATAR_SIGNATURE_MISMATCH, "file")
    }

    private companion object {
        private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
        private val ALLOWED_AVATAR_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        )
    }
}
