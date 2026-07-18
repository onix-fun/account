package com.onix.account.organizations

import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.patch
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.organizationRouting(controller: OrganizationController) {
    route("/api/organizations") {
        get("/context", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<OrganizationContextResponse> { } } }
        }) { controller.context(call) }

        get("", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<List<com.onix.account.domain.OrganizationDto>> { } } }
        }) { controller.listMine(call) }

        post("", {
            tags = setOf("Organizations")
            request { body<CreateOrganizationRequest> { } }
            response { code(HttpStatusCode.Created) { body<com.onix.account.domain.OrganizationDto> { } } }
        }) { controller.create(call) }

        get("/by-name/{orgName}", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<com.onix.account.domain.OrganizationDto> { } } }
        }) { controller.getByName(call) }

        get("/invitations", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<List<com.onix.account.domain.OrganizationInvitationDto>> { } } }
        }) { controller.invitations(call) }

        post("/invitations/{invitationId}/accept", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<com.onix.account.domain.OrganizationInvitationDto> { } } }
        }) { controller.acceptInvitation(call) }

        post("/invitations/{invitationId}/decline", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<com.onix.account.domain.OrganizationInvitationDto> { } } }
        }) { controller.declineInvitation(call) }

        patch("/{orgId}", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<com.onix.account.domain.OrganizationDto> { } } }
        }) { controller.update(call) }

        post("/{orgId}/avatar", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<com.onix.account.domain.OrganizationDto> { } } }
        }) { controller.uploadAvatar(call) }

        get("/{orgId}/members", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.OK) { body<List<com.onix.account.domain.OrganizationMemberDto>> { } } }
        }) { controller.members(call) }

        post("/{orgId}/invitations", {
            tags = setOf("Organizations")
            request { body<InviteOrganizationMemberRequest> { } }
            response { code(HttpStatusCode.Created) { body<com.onix.account.domain.OrganizationInvitationDto> { } } }
        }) { controller.invite(call) }

        put("/{orgId}/members/{userId}", {
            tags = setOf("Organizations")
            request { body<UpdateOrganizationMemberRequest> { } }
            response { code(HttpStatusCode.OK) { description = "Member role updated" } }
        }) { controller.updateMember(call) }

        delete("/{orgId}/members/{userId}", {
            tags = setOf("Organizations")
            response { code(HttpStatusCode.NoContent) { description = "Member removed" } }
        }) { controller.removeMember(call) }
    }
}
