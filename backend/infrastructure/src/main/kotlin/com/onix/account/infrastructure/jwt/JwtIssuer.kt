package com.onix.account.infrastructure.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.uuid.Generators
import com.onix.account.infrastructure.config.JwtConfig
import java.util.*

class JwtIssuer(config: JwtConfig) {
    private val issuer = config.issuer
    private val audience = config.audience
    private val activeKid = config.activeKid
    private val validityInMinutes = config.accessTokenExpMinutes
    private val algorithm = Algorithm.RSA256(
        RsaKeyLoader.loadPublicKey(config.publicKey),
        RsaKeyLoader.loadPrivateKey(config.privateKey)
    )

    fun createToken(
        userId: String,
        sessionId: String,
        activeOwnerType: String = "USER",
        activeOwnerId: String = userId
    ): String {
        val now = Date()
        return JWT.create()
            .withKeyId(activeKid)
            .withAudience(audience)
            .withIssuer(issuer)
            .withSubject(userId)
            .withJWTId(Generators.timeBasedEpochGenerator().generate().toString())
            .withIssuedAt(now)
            .withNotBefore(now)
            .withClaim("sid", sessionId)
            .withClaim("owner_type", activeOwnerType)
            .withClaim("owner_id", activeOwnerId)
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMinutes * 60 * 1000))
            .sign(algorithm)
    }
}
