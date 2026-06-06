package profile

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import profile.infrastructure.security.EmailNormalizer
import profile.infrastructure.security.TokenHasher
import profile.infrastructure.storage.AvatarProcessor

class SecurityHardeningTest {
    @Test
    fun `email normalizer rejects header injection`() {
        assertFails { EmailNormalizer.normalize("victim@example.com\r\nBcc: attacker@example.com") }
    }

    @Test
    fun `challenge hashes are scoped to subject and purpose`() {
        val secret = "test-secret"
        val code = "123456"
        assertNotEquals(
            TokenHasher.challenge(secret, "PASSWORD_RESET", "user-a", code),
            TokenHasher.challenge(secret, "EMAIL_VERIFICATION", "user-a", code)
        )
        assertNotEquals(
            TokenHasher.challenge(secret, "PASSWORD_RESET", "user-a", code),
            TokenHasher.challenge(secret, "PASSWORD_RESET", "user-b", code)
        )
    }

    @Test
    fun `avatar processor rejects undecodable input`() {
        assertFails { AvatarProcessor.process("not-an-image".toByteArray()) }
    }
}
