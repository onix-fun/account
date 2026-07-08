package bootstrap

import io.grpc.Grpc
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.netty.handler.ssl.ClientAuth
import org.slf4j.LoggerFactory
import profile.grpc.ProfileServiceImpl
import profile.api.grpc.AccessTokenServerInterceptor
import profile.api.grpc.SocialGrpcService
import java.io.File
import java.security.cert.X509Certificate

class ClientSanInterceptor(allowedSans: String) : ServerInterceptor {
    private val allowed = allowedSans.split(",").map(String::trim).filter(String::isNotBlank).toSet()
    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: io.grpc.Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val cert = call.attributes.get(Grpc.TRANSPORT_ATTR_SSL_SESSION)?.peerCertificates?.firstOrNull() as? X509Certificate
        val sans = cert?.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() }.orEmpty()
        if (allowed.isEmpty() || sans.none(allowed::contains)) {
            call.close(io.grpc.Status.PERMISSION_DENIED.withDescription("Client certificate SAN is not allowed"), io.grpc.Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        return next.startCall(call, headers)
    }
}

class GrpcServer(
    identityService: ProfileServiceImpl,
    socialGrpcService: SocialGrpcService,
    private val port: Int,
    certificate: String,
    privateKey: String,
    clientCa: String,
    allowedSans: String,
    reflection: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val builder = NettyServerBuilder.forPort(port)

    init {
        val accessTokenInterceptor = AccessTokenServerInterceptor()
        if (certificate.isNotBlank() && privateKey.isNotBlank() && clientCa.isNotBlank()) {
            val clientSanInterceptor = ClientSanInterceptor(allowedSans)
            builder.sslContext(GrpcSslContexts.forServer(pemFile(certificate, "certificate"), pemFile(privateKey, "private-key"))
                .trustManager(pemFile(clientCa, "client-ca")).clientAuth(ClientAuth.REQUIRE).build())
                .addService(ServerInterceptors.intercept(identityService, clientSanInterceptor, accessTokenInterceptor))
                .addService(ServerInterceptors.intercept(socialGrpcService, clientSanInterceptor, accessTokenInterceptor))
            log.info("Configured mTLS for gRPC server")
        } else {
            builder.addService(ServerInterceptors.intercept(identityService, accessTokenInterceptor))
            builder.addService(ServerInterceptors.intercept(socialGrpcService, accessTokenInterceptor))
            log.info("Configured insecure gRPC server (no certificates provided)")
        }
    }

    private val server = (if (reflection) builder.addService(ProtoReflectionService.newInstance()) else builder).build()
    fun start() { server.start(); log.info("Combined gRPC server started on port {}", port); Runtime.getRuntime().addShutdownHook(Thread { server.shutdown() }) }
    fun stop() { server.shutdown() }

    private fun pemFile(source: String, name: String): File {
        if (!source.startsWith("-----BEGIN")) return File(source)
        return kotlin.io.path.createTempFile("account-grpc-$name-", ".pem").toFile().apply {
            writeText(source)
            deleteOnExit()
        }
    }
}
