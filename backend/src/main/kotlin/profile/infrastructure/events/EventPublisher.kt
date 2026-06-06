package profile.infrastructure.events

import profile.infrastructure.config.SecurityConfig
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.sql.DataSource

class EventPublisher(private val dataSource: DataSource, security: SecurityConfig) {
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(security.internalAuthSecret.toByteArray()), "AES")

    fun publish(type: String, payload: String, expiresAt: Instant = Instant.now().plusSeconds(900)) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO email_outbox(event_type,payload,expires_at) VALUES (?,?,?)").use {
                it.setString(1, type); it.setString(2, encrypt(payload)); it.setTimestamp(3, java.sql.Timestamp.from(expiresAt)); it.executeUpdate()
            }; conn.commit()
        }
    }

    fun decrypt(payload: String): String {
        val bytes = Base64.getDecoder().decode(payload)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }

    private fun encrypt(payload: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(payload.toByteArray()))
    }
}
