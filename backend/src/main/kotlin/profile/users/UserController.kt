package profile.users

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import profile.infrastructure.db.User
import profile.shared.ApiErrorCode
import profile.shared.apiError
import profile.auth.AuthService
import profile.infrastructure.events.EmailLocale
import java.io.ByteArrayOutputStream
import java.io.InputStream

class UserController(
    private val userService: UserService,
    private val authService: AuthService
) {
    suspend fun getMe(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject

        val user = userService.getProfile(userId) ?: apiError(ApiErrorCode.USER_NOT_FOUND)

        call.respond(HttpStatusCode.OK, user.toProfileDto())
    }

    suspend fun updateMe(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        val request = call.receive<UpdateProfileRequest>()
        
        val updatedUser = userService.updateProfile(userId, request)
        call.respond(HttpStatusCode.OK, updatedUser.toProfileDto())
    }

    suspend fun requestEmailChange(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()!!
        val request = call.receive<RequestEmailChangeRequest>()
        authService.requestEmailChange(principal.payload.subject, request.currentPassword, request.newEmail, call.emailLocale())
        call.respond(HttpStatusCode.Accepted, profile.auth.CodeSentResponse())
    }

    suspend fun confirmEmailChange(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()!!
        val request = call.receive<ConfirmEmailChangeRequest>()
        authService.confirmEmailChange(principal.payload.subject, principal.payload.getClaim("sid").asString(), request.code, call.emailLocale())
        call.respond(HttpStatusCode.OK)
    }

    suspend fun cancelEmailChange(call: ApplicationCall) {
        authService.cancelEmailChange(call.principal<JWTPrincipal>()!!.payload.subject)
        call.respond(HttpStatusCode.NoContent)
    }

    suspend fun uploadAvatar(call: ApplicationCall) {
        val userId = call.principal<JWTPrincipal>()!!.payload.subject
        
        val multipart = call.receiveMultipart()
        var user: User? = null
        
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val contentType = part.contentType?.withoutParameters()?.toString()?.lowercase()
                    ?: apiError(ApiErrorCode.AVATAR_CONTENT_TYPE_REQUIRED, "file")

                if (contentType !in ALLOWED_AVATAR_TYPES) {
                    apiError(ApiErrorCode.AVATAR_UNSUPPORTED_TYPE, "file")
                }

                val fileBytes = part.streamProvider().use { it.readLimited(MAX_AVATAR_BYTES) }
                validateAvatarSignature(fileBytes, contentType)
                
                user = userService.updateAvatar(userId, fileBytes, contentType)
            }
            part.dispose()
        }
        
        if (user != null) {
            call.respond(HttpStatusCode.OK, user!!.toProfileDto())
        } else {
            apiError(ApiErrorCode.AVATAR_FILE_REQUIRED, "file")
        }
    }

    suspend fun getById(call: ApplicationCall) {
        val id = call.parameters["id"] ?: apiError(ApiErrorCode.VALIDATION_REQUIRED_FIELD, "id")
        
        try {
            java.util.UUID.fromString(id)
        } catch (e: Exception) {
            apiError(ApiErrorCode.VALIDATION_INVALID_UUID, "id")
        }

        val user = userService.getProfile(id)
        if (user == null) apiError(ApiErrorCode.USER_NOT_FOUND)

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
            if (total > maxBytes) apiError(ApiErrorCode.AVATAR_TOO_LARGE, "file")
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }

    private fun ApplicationCall.emailLocale(): EmailLocale =
        EmailLocale.fromHeader(request.headers[HttpHeaders.AcceptLanguage])

    private fun validateAvatarSignature(bytes: ByteArray, contentType: String) {
        val valid = when (contentType) {
            "image/jpeg" -> bytes.size >= 3 &&
                bytes[0] == 0xff.toByte() &&
                bytes[1] == 0xd8.toByte() &&
                bytes[2] == 0xff.toByte()
            "image/png" -> bytes.size >= PNG_SIGNATURE.size &&
                bytes.take(PNG_SIGNATURE.size).toByteArray().contentEquals(PNG_SIGNATURE)
            "image/webp" -> bytes.size >= 12 &&
                bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
            else -> false
        }
        if (!valid) apiError(ApiErrorCode.AVATAR_SIGNATURE_MISMATCH, "file")
    }

    private companion object {
        private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
        private const val DEFAULT_BUFFER_SIZE = 8192
        private val ALLOWED_AVATAR_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        )
    }
}
