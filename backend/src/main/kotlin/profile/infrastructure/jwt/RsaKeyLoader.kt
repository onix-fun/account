package profile.infrastructure.jwt

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object RsaKeyLoader {
    fun loadPrivateKey(source: String): RSAPrivateKey {
        val bytes = decodePem(source, "PRIVATE KEY")
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes)) as RSAPrivateKey
    }

    fun loadPublicKey(source: String): RSAPublicKey {
        val bytes = decodePem(source, "PUBLIC KEY")
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes)) as RSAPublicKey
    }

    private fun decodePem(source: String, type: String): ByteArray {
        val pem = if (source.startsWith("-----BEGIN")) source else Files.readString(Path.of(source))
        val encoded = pem
            .replace("-----BEGIN $type-----", "")
            .replace("-----END $type-----", "")
            .replace(Regex("\\s"), "")
        require(encoded.isNotBlank()) { "RSA $type PEM is empty" }
        return Base64.getDecoder().decode(encoded)
    }
}
