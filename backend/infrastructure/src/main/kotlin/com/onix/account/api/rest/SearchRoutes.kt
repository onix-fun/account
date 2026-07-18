package com.onix.account.api.rest

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.onix.account.domain.OwnerRef
import com.onix.account.domain.OwnerType
import com.onix.account.organizations.OrganizationService
import com.onix.account.search.SearchService
import com.onix.account.usecases.SocialUseCases
import java.util.UUID

fun Route.searchRoutes(
    searchService: SearchService,
    socialUseCases: SocialUseCases,
    organizationService: OrganizationService
) {
    route("/api/profile/search") {
        get {
            val uid = requireUserId(call)
            val activeOwner = activeOwnerRef(call)
            val query = call.request.queryParameters["q"] ?: ""
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            val users = searchService.searchByUsernamePrefix(query, limit)
                .filter { it.id != uid.toString() }
                .map { user ->
                    val target = OwnerRef.user(UUID.fromString(user.id))
                    val relationship = socialUseCases.getRelationship(activeOwner, target)
                    user.withRelationship(relationship.toResponse()).copy(
                        organizationMembershipState = if (activeOwner.type == OwnerType.ORGANIZATION) {
                            organizationService.membershipState(activeOwner.id.toString(), user.id)
                        } else null
                    )
                }
            val organizations = organizationService.search(query, limit)
                .filterNot { activeOwner.type == OwnerType.ORGANIZATION && it.id == activeOwner.id.toString() }
                .map { organization ->
                    val target = OwnerRef.organization(UUID.fromString(organization.id))
                    PublicUserRelationshipResponse(
                        id = organization.id,
                        ownerType = OwnerType.ORGANIZATION.name,
                        username = organization.orgName,
                        displayName = organization.displayName,
                        avatarUrl = organization.avatarUrl,
                        bio = organization.bio,
                        relationship = socialUseCases.getRelationship(activeOwner, target).toResponse()
                    )
                }
            call.respond((users + organizations).take(limit.coerceIn(1, 50)))
        }
    }
}
