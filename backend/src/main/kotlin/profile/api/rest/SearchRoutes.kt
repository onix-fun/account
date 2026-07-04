package profile.api.rest

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import profile.search.SearchService
import profile.usecases.SocialUseCases
import java.util.UUID

fun Route.searchRoutes(searchService: SearchService, socialUseCases: SocialUseCases) {
    route("/api/profile/search") {
        get {
            val uid = requireUserId(call)
            val query = call.request.queryParameters["q"] ?: ""
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
            val results = searchService.searchByUsernamePrefix(query, limit)
                .filter { it.id != uid.toString() }
                .map { user ->
                    val relationship = socialUseCases.getRelationship(uid, UUID.fromString(user.id))
                    user.withRelationship(relationship.toResponse())
                }
            call.respond(results)
        }
    }
}
