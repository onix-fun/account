package profile.infrastructure.security

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TokenHasher {
    fun hash(token: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun challenge(secret: String, purpose: String, subjectId: String, code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal("$purpose:$subjectId:$code".toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
