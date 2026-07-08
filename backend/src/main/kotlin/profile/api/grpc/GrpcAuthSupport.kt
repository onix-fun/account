package profile.api.grpc

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import profile.infrastructure.config.JwtConfig
import profile.infrastructure.db.SessionRepository
import profile.infrastructure.jwt.RsaKeyLoader
import java.time.Instant

data class GrpcPrincipal(
    val userId: String,
    val sessionId: String,
    val activeOwnerType: String,
    val activeOwnerId: String
)

object GrpcAuthContext {
    val ACCESS_TOKEN: Context.Key<String> = Context.key("account-access-token")
}

class AccessTokenServerInterceptor : ServerInterceptor {
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val token = headers.get(AUTHORIZATION_KEY)
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")
            ?.takeIf(String::isNotBlank)
            ?: headers.get(ACCESS_TOKEN_KEY)?.takeIf(String::isNotBlank)
        val context = Context.current().withValue(GrpcAuthContext.ACCESS_TOKEN, token)
        return Contexts.interceptCall(context, call, headers, next)
    }

    private companion object {
        val AUTHORIZATION_KEY: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        val ACCESS_TOKEN_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-account-access-token", Metadata.ASCII_STRING_MARSHALLER)
    }
}

class GrpcPrincipalResolver(
    jwtConfig: JwtConfig,
    private val sessionRepository: SessionRepository
) {
    private val verifier = JWT.require(Algorithm.RSA256(RsaKeyLoader.loadPublicKey(jwtConfig.publicKey), null))
        .withIssuer(jwtConfig.issuer)
        .withAudience(jwtConfig.audience)
        .build()

    fun requireUserId(): String {
        return requirePrincipal().userId
    }

    fun requirePrincipal(): GrpcPrincipal {
        val token = GrpcAuthContext.ACCESS_TOKEN.get()
            ?: throw Status.UNAUTHENTICATED.withDescription("access token is required").asRuntimeException()
        val payload = runCatching { verifier.verify(token) }.getOrElse {
            throw Status.UNAUTHENTICATED.withDescription("access token is invalid").asRuntimeException()
        }
        val sessionId = payload.getClaim("sid").asString()
            ?: throw Status.UNAUTHENTICATED.withDescription("session claim is required").asRuntimeException()
        val session = sessionRepository.findById(sessionId)
        if (session == null || session.revokedAt != null || session.expiresAt.isBefore(Instant.now())) {
            throw Status.UNAUTHENTICATED.withDescription("session is not active").asRuntimeException()
        }
        return GrpcPrincipal(
            userId = payload.subject,
            sessionId = sessionId,
            activeOwnerType = session.activeOwnerType,
            activeOwnerId = session.activeOwnerId
        )
    }
}
