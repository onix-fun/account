package profile.infrastructure.events

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import profile.infrastructure.config.SmtpConfig
import profile.infrastructure.security.EmailNormalizer
import java.util.Properties

class SmtpEmailSender(private val config: SmtpConfig) {
    fun sendVerificationCode(email: String, code: String) = send(email, "Account email verification", "Your verification code is:\n\n$code\n\nThe code expires in 15 minutes.")
    fun sendPasswordReset(email: String, code: String) = send(email, "Account password reset", "Your password reset code is:\n\n$code\n\nThe code expires in 15 minutes.")
    fun sendSecurityNotification(email: String, message: String) = send(email, "Account security notification", message)

    private fun send(to: String, subject: String, body: String) {
        val recipient = EmailNormalizer.normalize(to)
        val from = EmailNormalizer.normalize(config.from, "from")
        val properties = Properties().apply {
            put("mail.smtp.host", config.host); put("mail.smtp.port", config.port.toString())
            put("mail.smtp.starttls.enable", config.startTls.toString()); put("mail.smtp.auth", (config.username != null).toString())
        }
        val session = Session.getInstance(properties, if (config.username != null) object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(config.username, config.password.orEmpty())
        } else null)
        Transport.send(MimeMessage(session).apply {
            setFrom(InternetAddress(from)); setRecipient(Message.RecipientType.TO, InternetAddress(recipient))
            setSubject(subject, Charsets.UTF_8.name()); setText(body, Charsets.UTF_8.name())
        })
    }
}
