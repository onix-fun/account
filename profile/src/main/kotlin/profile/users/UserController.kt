package profile.users

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import profile.infrastructure.db.User
import java.io.ByteArrayOutputStream
import java.io.InputStream

class UserController(
    private val userService: UserService
) {
    suspend fun getMe(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject

        val user = userService.getProfile(userId)
        if (user == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            return
        }

        call.respond(HttpStatusCode.OK, user.toProfileDto())
    }

    suspend fun updateMe(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val request = call.receive<UpdateProfileRequest>()
        
        val updatedUser = userService.updateProfile(userId, request)
        call.respond(HttpStatusCode.OK, updatedUser.toProfileDto())
    }

    suspend fun uploadAvatar(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        
        val multipart = call.receiveMultipart()
        var user: User? = null
        
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val contentType = part.contentType?.withoutParameters()?.toString()?.lowercase()
                    ?: throw IllegalArgumentException("Avatar content type is required")

                if (contentType !in ALLOWED_AVATAR_TYPES) {
                    throw IllegalArgumentException("Avatar must be a JPEG, PNG, or WebP image")
                }

                val fileBytes = part.streamProvider().use { it.readLimited(MAX_AVATAR_BYTES) }
                
                user = userService.updateAvatar(userId, fileBytes, contentType)
            }
            part.dispose()
        }
        
        if (user != null) {
            call.respond(HttpStatusCode.OK, user!!.toProfileDto())
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
        }
    }

    suspend fun getById(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing ID")
        
        try {
            java.util.UUID.fromString(id)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid UUID format"))
            return
        }

        val user = userService.getProfile(id)
        if (user == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            return
        }

        call.respond(HttpStatusCode.OK, user.toPublicDto())
    }

    private fun InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0

        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw IllegalArgumentException("Avatar must be 5MB or smaller")
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }

    private companion object {
        private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
        private const val DEFAULT_BUFFER_SIZE = 8192
        private val ALLOWED_AVATAR_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
