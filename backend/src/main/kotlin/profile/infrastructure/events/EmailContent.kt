package profile.infrastructure.events

import kotlinx.serialization.Serializable

@Serializable
enum class EmailLocale {
    EN,
    RU;

    companion object {
        fun fromHeader(value: String?): EmailLocale =
            if (value?.trim()?.lowercase()?.startsWith("ru") == true) RU else EN
    }
}

@Serializable
enum class EmailCodePurpose {
    VERIFY_EMAIL,
    CHANGE_EMAIL,
    RESET_PASSWORD
}

@Serializable
enum class SecurityNoticeType {
    PASSWORD_RESET,
    PASSWORD_CHANGED,
    EMAIL_CHANGED,
    EMAIL_ADDED
}

data class EmailContent(
    val subject: String,
    val text: String,
    val html: String
)

object EmailContentFactory {
    fun code(purpose: EmailCodePurpose, code: String, locale: EmailLocale): EmailContent {
        val copy = when (locale) {
            EmailLocale.RU -> when (purpose) {
                EmailCodePurpose.VERIFY_EMAIL -> Copy("Подтверждение email", "Код подтверждения email", "Введите этот код, чтобы подтвердить email.")
                EmailCodePurpose.CHANGE_EMAIL -> Copy("Смена email", "Код подтверждения нового email", "Введите этот код, чтобы завершить смену email.")
                EmailCodePurpose.RESET_PASSWORD -> Copy("Сброс пароля", "Код сброса пароля", "Введите этот код, чтобы установить новый пароль.")
            }
            EmailLocale.EN -> when (purpose) {
                EmailCodePurpose.VERIFY_EMAIL -> Copy("Email verification", "Email verification code", "Enter this code to verify your email address.")
                EmailCodePurpose.CHANGE_EMAIL -> Copy("Change email", "New email verification code", "Enter this code to complete your email change.")
                EmailCodePurpose.RESET_PASSWORD -> Copy("Password reset", "Password reset code", "Enter this code to set a new password.")
            }
        }
        val expiry = if (locale == EmailLocale.RU) "Код действует 15 минут." else "The code expires in 15 minutes."
        val ignore = if (locale == EmailLocale.RU) "Если вы не запрашивали это действие, просто проигнорируйте письмо." else "If you did not request this action, you can ignore this email."
        return content(copy.subject, copy.title, copy.description, code, expiry, ignore, locale)
    }

    fun security(type: SecurityNoticeType, locale: EmailLocale): EmailContent {
        val copy = when (locale) {
            EmailLocale.RU -> when (type) {
                SecurityNoticeType.PASSWORD_RESET -> Copy("Пароль сброшен", "Пароль был сброшен", "Пароль вашего аккаунта успешно сброшен.")
                SecurityNoticeType.PASSWORD_CHANGED -> Copy("Пароль изменён", "Пароль был изменён", "Пароль вашего аккаунта успешно изменён.")
                SecurityNoticeType.EMAIL_CHANGED -> Copy("Email изменён", "Email аккаунта был изменён", "Для вашего аккаунта установлен новый email.")
                SecurityNoticeType.EMAIL_ADDED -> Copy("Email добавлен", "Этот email добавлен к аккаунту", "Этот адрес теперь используется для вашего аккаунта.")
            }
            EmailLocale.EN -> when (type) {
                SecurityNoticeType.PASSWORD_RESET -> Copy("Password reset", "Your password was reset", "Your account password was successfully reset.")
                SecurityNoticeType.PASSWORD_CHANGED -> Copy("Password changed", "Your password was changed", "Your account password was successfully changed.")
                SecurityNoticeType.EMAIL_CHANGED -> Copy("Email changed", "Your account email was changed", "A new email address was set for your account.")
                SecurityNoticeType.EMAIL_ADDED -> Copy("Email added", "This email was added to an account", "This address is now used for your account.")
            }
        }
        val warning = if (locale == EmailLocale.RU) {
            "Если это сделали не вы, немедленно восстановите доступ к аккаунту."
        } else {
            "If this was not you, recover access to your account immediately."
        }
        return content(copy.subject, copy.title, copy.description, null, null, warning, locale)
    }

    private fun content(
        subject: String,
        title: String,
        description: String,
        code: String?,
        detail: String?,
        footer: String,
        locale: EmailLocale
    ): EmailContent {
        val brand = "Account"
        val text = listOfNotNull(title, description, code, detail, footer).joinToString("\n\n")
        val direction = "ltr"
        val codeBlock = code?.let {
            """<div style="margin:24px 0;padding:18px 20px;border-radius:12px;background:#edf0f2;color:#172033;font-size:30px;font-weight:700;letter-spacing:8px;text-align:center;">$it</div>"""
        }.orEmpty()
        val detailBlock = detail?.let {
            """<p style="margin:0 0 20px;color:#667085;font-size:13px;line-height:1.55;">$it</p>"""
        }.orEmpty()
        val html = """
            <!doctype html>
            <html lang="${locale.name.lowercase()}" dir="$direction">
            <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;color:#172033;">
              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f4f6f8;padding:28px 12px;">
                <tr><td align="center">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:16px;">
                    <tr><td style="padding:20px 24px;background:#30363d;border-radius:16px 16px 0 0;color:#ffffff;font-size:16px;font-weight:700;">$brand</td></tr>
                    <tr><td style="padding:28px 24px;">
                      <h1 style="margin:0 0 12px;font-size:24px;line-height:1.25;color:#172033;">$title</h1>
                      <p style="margin:0;color:#667085;font-size:15px;line-height:1.6;">$description</p>
                      $codeBlock
                      $detailBlock
                      <div style="padding:14px 16px;border-radius:10px;background:#edf0f2;color:#667085;font-size:12px;line-height:1.55;">$footer</div>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
        """.trimIndent()
        return EmailContent(subject, text, html)
    }

    private data class Copy(val subject: String, val title: String, val description: String)
}
