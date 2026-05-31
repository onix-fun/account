package profile.search

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.routing.*
import profile.users.UserPublicDto

fun Route.searchRouting(searchController: SearchController) {
    route("/api/search") {
        get("/id-by-username/{username}", {
            tags = setOf("Search")
            summary = "Get user ID by username"
            description = "Resolves a username to a user ID"
            request { pathParameter<String>("username") { description = "Username" } }
            response {
                code(HttpStatusCode.OK) { description = "User ID" }
                code(HttpStatusCode.NotFound) { description = "User not found" }
            }
        }) { searchController.getIdByUsername(call) }

        get("/search", {
            tags = setOf("Search")
            summary = "Search users"
            description = "Search users by username prefix"
            request { queryParameter<String>("q") { description = "Search query (min 2 chars)" } }
            response { code(HttpStatusCode.OK) { description = "Matching users"; body<List<UserPublicDto>> { } } }
        }) { searchController.search(call) }
    }
}
