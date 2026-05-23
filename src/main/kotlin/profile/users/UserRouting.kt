package profile.users

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.patch
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.routing.*

fun Route.userRouting(userController: UserController) {
    route("/api/users") {
        get("/me", {
            summary = "Get current user"
            description = "Returns authenticated user's profile"
            response { code(HttpStatusCode.OK) { description = "User profile"; body<UserPublicDto> { } } }
        }) { userController.getMe(call) }

        patch("/me", {
            summary = "Update profile"
            description = "Updates authenticated user's profile fields"
            request { body<UpdateProfileRequest> { description = "Fields to update" } }
            response { code(HttpStatusCode.OK) { description = "Updated user"; body<UserPublicDto> { } } }
        }) { userController.updateMe(call) }

        post("/me/avatar", {
            summary = "Upload avatar"
            description = "Uploads a new avatar image"
            response { code(HttpStatusCode.OK) { body<UserPublicDto> { } } }
        }) { userController.uploadAvatar(call) }

        get("/{id}", {
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
