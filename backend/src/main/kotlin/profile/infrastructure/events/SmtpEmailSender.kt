package profile.infrastructure.events

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import profile.infrastructure.config.SmtpConfig
import profile.infrastructure.security.EmailNormalizer
import java.util.Properties

class SmtpEmailSender(private val config: SmtpConfig) {
    fun sendCode(email: String, code: String, purpose: EmailCodePurpose, locale: EmailLocale) =
        send(email, EmailContentFactory.code(purpose, code, locale))

    fun sendSecurityNotification(email: String, type: SecurityNoticeType, locale: EmailLocale) =
        send(email, EmailContentFactory.security(type, locale))

    private fun send(to: String, content: EmailContent) {
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
            setSubject(content.subject, Charsets.UTF_8.name())
            setContent(MimeMultipart("alternative").apply {
                addBodyPart(MimeBodyPart().apply { setText(content.text, Charsets.UTF_8.name()) })
                addBodyPart(MimeBodyPart().apply { setContent(content.html, "text/html; charset=UTF-8") })
            })
        })
    }
}
