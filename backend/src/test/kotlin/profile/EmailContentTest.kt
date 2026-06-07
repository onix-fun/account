package profile

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import profile.infrastructure.events.EmailCodePurpose
import profile.infrastructure.events.EmailContentFactory
import profile.infrastructure.events.EmailLocale
import profile.infrastructure.events.SecurityNoticeType

class EmailContentTest {
    @Test
    fun `accept language selects supported locale`() {
        assertEquals(EmailLocale.RU, EmailLocale.fromHeader("ru-RU,ru;q=0.9,en;q=0.8"))
        assertEquals(EmailLocale.EN, EmailLocale.fromHeader("en-US"))
        assertEquals(EmailLocale.EN, EmailLocale.fromHeader(null))
    }

    @Test
    fun `code emails contain localized html and text alternatives`() {
        val content = EmailContentFactory.code(EmailCodePurpose.RESET_PASSWORD, "123456", EmailLocale.RU)

        assertContains(content.subject, "Сброс")
        assertContains(content.text, "123456")
        assertContains(content.html, "123456")
        assertContains(content.html, "background:#30363d")
    }

    @Test
    fun `security emails are localized by typed notice`() {
        val content = EmailContentFactory.security(SecurityNoticeType.PASSWORD_CHANGED, EmailLocale.EN)

        assertContains(content.subject, "Password changed")
        assertContains(content.text, "If this was not you")
        assertContains(content.html, "Your password was changed")
    }
}
