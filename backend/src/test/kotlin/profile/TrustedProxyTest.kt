package profile

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import profile.infrastructure.security.TrustedProxy

class TrustedProxyTest {
    @Test
    fun `matches trusted IPv4 and IPv6 CIDRs`() {
        assertTrue(TrustedProxy.contains("172.18.0.4", listOf("172.16.0.0/12")))
        assertTrue(TrustedProxy.contains("::1", listOf("::1/128")))
        assertFalse(TrustedProxy.contains("192.168.1.2", listOf("172.16.0.0/12")))
    }

    @Test
    fun `rejects invalid addresses and CIDRs`() {
        assertFalse(TrustedProxy.contains("not-an-ip.invalid", listOf("172.16.0.0/12")))
        assertFalse(TrustedProxy.contains("172.18.0.4", listOf("172.16.0.0/99")))
    }
}
