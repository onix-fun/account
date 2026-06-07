package profile.infrastructure.security

import java.net.InetAddress

object TrustedProxy {
    fun contains(address: String, cidrs: List<String>): Boolean = runCatching {
        val candidate = InetAddress.getByName(address).address
        cidrs.any { cidr ->
            val parts = cidr.trim().split("/", limit = 2)
            val network = InetAddress.getByName(parts[0]).address
            val prefix = parts.getOrNull(1)?.toIntOrNull() ?: network.size * 8
            candidate.size == network.size && matches(candidate, network, prefix)
        }
    }.getOrDefault(false)

    private fun matches(candidate: ByteArray, network: ByteArray, prefix: Int): Boolean {
        if (prefix !in 0..candidate.size * 8) return false
        val fullBytes = prefix / 8
        val remainingBits = prefix % 8
        if ((0 until fullBytes).any { candidate[it] != network[it] }) return false
        if (remainingBits == 0) return true
        val mask = (0xFF shl (8 - remainingBits)) and 0xFF
        return (candidate[fullBytes].toInt() and mask) == (network[fullBytes].toInt() and mask)
    }
}
