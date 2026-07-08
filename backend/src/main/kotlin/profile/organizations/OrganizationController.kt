package profile.organizations

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import profile.domain.OwnerRef
import profile.domain.OwnerType
import profile.domain.OrganizationRole
import profile.shared.ApiErrorCode
import profile.shared.apiError
import java.util.UUID

class OrganizationController(private val organizationService: OrganizationService) {
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
            displayName = body["displayName"]?.jsonPrimitive?.contentOrNull,
            bio = body["bio"]?.jsonPrimitive?.contentOrNull,
            bioProvided = body.containsKey("bio")
        )
        call.respond(HttpStatusCode.OK, organizationService.update(userId(call), requireOrgId(call), request))
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
}
