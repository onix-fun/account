package com.onix.account.infrastructure.jwt

import kotlinx.serialization.Serializable
import com.onix.account.infrastructure.config.JwtConfig
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey
import java.util.Base64

@Serializable
data class Jwk(val kty: String = "RSA", val use: String = "sig", val alg: String = "RS256", val kid: String, val n: String, val e: String)

@Serializable
data class Jwks(val keys: List<Jwk>)

class JwksProvider(config: JwtConfig) {
    val document = Jwks(
        buildList {
            add(toJwk(config.activeKid, RsaKeyLoader.loadPublicKey(config.publicKey)))
            config.previousPublicKeys.forEach { (kid, source) -> add(toJwk(kid, RsaKeyLoader.loadPublicKey(source))) }
        }
    )

    private fun toJwk(kid: String, key: RSAPublicKey) = Jwk(kid = kid, n = encode(key.modulus), e = encode(key.publicExponent))

    private fun encode(value: BigInteger): String {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
