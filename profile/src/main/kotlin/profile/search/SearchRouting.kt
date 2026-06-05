package profile.search

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import profile.users.UserPublicDto

fun Route.searchRouting(searchController: SearchController) {
    route("/api/search") {
        authenticate {
            get("/search", {
            tags = setOf("Search")
            summary = "Search users"
            description = "Search users by username prefix"
            request { queryParameter<String>("q") { description = "Search query (min 2 chars)" } }
            response { code(HttpStatusCode.OK) { description = "Matching users"; body<List<UserPublicDto>> { } } }
            }) { searchController.search(call) }
        }
    }
}
