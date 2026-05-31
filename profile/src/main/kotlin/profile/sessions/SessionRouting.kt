package profile.sessions

import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Route.sessionRouting(sessionController: SessionController) {
    route("/api/sessions") {
        authenticate {
            get({
                tags = setOf("Sessions")
                securitySchemeNames("BearerToken")
                summary = "List sessions"
                description = "Returns all active sessions for the authenticated user"
                response { code(HttpStatusCode.OK) { description = "List of sessions"; body<List<SessionInfoDto>> { } } }
            }) { sessionController.getSessions(call) }

            delete("/{id}", {
                tags = setOf("Sessions")
                securitySchemeNames("BearerToken")
                summary = "Revoke session"
                description = "Revokes a specific session by ID"
                request { pathParameter<String>("id") { description = "Session UUID" } }
                response { code(HttpStatusCode.OK) { description = "Session revoked" } }
            }) { sessionController.revokeSession(call) }
        }
    }
}
