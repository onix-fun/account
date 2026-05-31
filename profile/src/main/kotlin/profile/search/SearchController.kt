package profile.search

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import profile.users.UserPublicDto

class SearchController(private val searchService: SearchService) {

    suspend fun getIdByUsername(call: ApplicationCall) {
        val username = call.parameters["username"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing username")
        val userId = searchService.getUserIdByUsername(username)
        
        if (userId == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            return
        }

        call.respond(HttpStatusCode.OK, mapOf("id" to userId))
    }

    suspend fun search(call: ApplicationCall) {
        val query = call.request.queryParameters["q"]
        if (query.isNullOrBlank()) {
            call.respond(HttpStatusCode.OK, emptyList<UserPublicDto>())
            return
        }

        if (query.length < 2) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query must be at least 2 characters long"))
            return
        }

        val results = searchService.searchByUsernamePrefix(query)
        call.respond(HttpStatusCode.OK, results)
    }
}
