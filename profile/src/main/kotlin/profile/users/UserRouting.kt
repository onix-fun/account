package profile.users

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.patch
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Route.userRouting(userController: UserController) {
    route("/api/users") {
        authenticate {
            get("/me", {
                tags = setOf("Users")
                securitySchemeNames("BearerToken")
                summary = "Get current user"
                description = "Returns authenticated user's profile"
                response { code(HttpStatusCode.OK) { description = "User profile"; body<UserProfileDto> { } } }
            }) { userController.getMe(call) }

            patch("/me", {
                tags = setOf("Users")
                securitySchemeNames("BearerToken")
                summary = "Update profile"
                description = "Updates authenticated user's profile fields"
                request { body<UpdateProfileRequest> { description = "Fields to update" } }
                response { code(HttpStatusCode.OK) { description = "Updated user"; body<UserProfileDto> { } } }
            }) { userController.updateMe(call) }

            post("/me/avatar", {
                tags = setOf("Users")
                securitySchemeNames("BearerToken")
                summary = "Upload avatar"
                description = "Uploads a new avatar image"
                response { code(HttpStatusCode.OK) { body<UserProfileDto> { } } }
            }) { userController.uploadAvatar(call) }
        }

        get("/{id}", {
            tags = setOf("Users")
            summary = "Get user by ID"
            description = "Returns public profile of any user"
            request { pathParameter<String>("id") { description = "User UUID" } }
            response {
                code(HttpStatusCode.OK) { description = "Public user profile"; body<UserPublicDto> { } }
                code(HttpStatusCode.NotFound) { description = "User not found" }
            }
        }) { userController.getById(call) }
    }
}
