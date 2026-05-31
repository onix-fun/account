package profile.infrastructure.security

import java.security.MessageDigest

object TokenHasher {
    fun hash(token: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
