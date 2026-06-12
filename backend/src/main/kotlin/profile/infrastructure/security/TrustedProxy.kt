package profile.infrastructure.security

import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address

object TrustedProxy {
    fun contains(address: String, cidrs: List<String>): Boolean = runCatching {
        val candidate = InetAddress.getByName(address)
        val candidateBytes = normalizedBytes(candidate) ?: return false

        cidrs.any { cidr ->
            val parts = cidr.trim().split("/", limit = 2)
            val network = InetAddress.getByName(parts[0])
            val networkBytes = normalizedBytes(network) ?: return@any false
            val prefix = parts.getOrNull(1)?.toIntOrNull() ?: (networkBytes.size * 8)

            candidateBytes.size == networkBytes.size && matches(candidateBytes, networkBytes, prefix)
        }
    }.getOrDefault(false)

    private fun normalizedBytes(address: InetAddress): ByteArray? {
        return when (address) {
            is Inet4Address -> address.address
            is Inet6Address -> {
                if (address.isIPv4CompatibleAddress) {
                    address.address.copyOfRange(12, 16)
                } else {
                    val bytes = address.address
                    // Check for IPv4-mapped IPv6 address (::ffff:0:0/96)
                    if (bytes.size == 16 && 
                        bytes[0] == 0.toByte() && bytes[1] == 0.toByte() &&
                        bytes[2] == 0.toByte() && bytes[3] == 0.toByte() &&
                        bytes[4] == 0.toByte() && bytes[5] == 0.toByte() &&
                        bytes[6] == 0.toByte() && bytes[7] == 0.toByte() &&
                        bytes[8] == 0.toByte() && bytes[9] == 0.toByte() &&
                        bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()) {
                        bytes.copyOfRange(12, 16)
                    } else {
                        bytes
                    }
                }
            }
            else -> null
        }
    }

    private fun matches(candidate: ByteArray, network: ByteArray, prefix: Int): Boolean {
        if (prefix <= 0) return true
        if (prefix > candidate.size * 8) return false
        val fullBytes = prefix / 8
        val remainingBits = prefix % 8
        for (i in 0 until fullBytes) {
            if (candidate[i] != network[i]) return false
        }
        if (remainingBits == 0) return true
        val mask = (0xFF shl (8 - remainingBits)) and 0xFF
        return (candidate[fullBytes].toInt() and mask) == (network[fullBytes].toInt() and mask)
    }
}
