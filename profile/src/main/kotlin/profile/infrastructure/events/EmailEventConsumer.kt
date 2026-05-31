package profile.infrastructure.events

import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import profile.infrastructure.redis.RedisManager
import org.slf4j.LoggerFactory

class EmailEventConsumer(
    private val redisManager: RedisManager,
    private val emailSender: SmtpEmailSender
) {
    private val logger = LoggerFactory.getLogger(EmailEventConsumer::class.java)

    fun start() {
        val pubSubConn = redisManager.pubSubConnection() ?: run {
            logger.warn("Redis PubSub connection not available. Email consumer disabled.")
            return
        }

        pubSubConn.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                if (channel == "identity_events") {
                    handleEvent(message)
                }
            }
        })

        pubSubConn.async().subscribe("identity_events")
        logger.info("Email event consumer started (listening to identity_events)")
    }

    private fun handleEvent(json: String) {
        try {
            val event = Json.parseToJsonElement(json).jsonObject
            val type = event["type"]?.jsonPrimitive?.content ?: return
            val payloadStr = event["payload"]?.jsonPrimitive?.content ?: return
            
            when (type) {
                "email.verify" -> {
                    val payload = Json.decodeFromString<VerificationEmailPayload>(payloadStr)
                    emailSender.sendVerificationCode(payload.email, payload.code)
                    logger.info("Sent verification email to ${payload.email}")
                }
                "email.password_reset" -> {
                    val payload = Json.decodeFromString<PasswordResetEmailPayload>(payloadStr)
                    emailSender.sendPasswordReset(payload.email, payload.code)
                    logger.info("Sent password reset email to ${payload.email}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process email event: ${e.message}")
        }
    }
}
