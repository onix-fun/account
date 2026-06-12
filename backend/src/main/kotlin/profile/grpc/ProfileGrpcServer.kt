package profile.grpc

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.Grpc
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyServerBuilder
import io.netty.handler.ssl.ClientAuth
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import profile.infrastructure.db.UserRepository
import profile.v1.GetUserByUsernameRequest
import profile.v1.ProfileServiceGrpc
import profile.v1.UserRef
import java.io.File
import java.security.cert.X509Certificate

class ClientSanInterceptor(allowedSans: String) : ServerInterceptor {
    private val allowed = allowedSans.split(",").map(String::trim).filter(String::isNotBlank).toSet()
    override fun <ReqT : Any, RespT : Any> interceptCall(call: ServerCall<ReqT, RespT>, headers: io.grpc.Metadata, next: ServerCallHandler<ReqT, RespT>): ServerCall.Listener<ReqT> {
        val cert = call.attributes.get(Grpc.TRANSPORT_ATTR_SSL_SESSION)?.peerCertificates?.firstOrNull() as? X509Certificate
        val sans = cert?.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() }.orEmpty()
        if (allowed.isEmpty() || sans.none(allowed::contains)) {
            call.close(Status.PERMISSION_DENIED.withDescription("Client certificate SAN is not allowed"), io.grpc.Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        return next.startCall(call, headers)
    }
}

class ProfileServiceImpl(private val users: UserRepository) : ProfileServiceGrpc.ProfileServiceImplBase() {
    override fun getUserByUsername(request: GetUserByUsernameRequest, observer: StreamObserver<UserRef>) {
        val user = request.username.takeIf { it.isNotBlank() }?.let(users::findByUsername)
        if (user == null) return observer.onError(StatusException(Status.NOT_FOUND))
        observer.onNext(UserRef.newBuilder().setId(user.id).setUsername(user.username).build()); observer.onCompleted()
    }
}

class ProfileGrpcServer(users: UserRepository, private val port: Int, certificate: String, privateKey: String, clientCa: String, allowedSans: String, reflection: Boolean) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val builder = NettyServerBuilder.forPort(port)
        
    init {
        if (certificate.isNotBlank() && privateKey.isNotBlank() && clientCa.isNotBlank()) {
            builder.sslContext(GrpcSslContexts.forServer(File(certificate), File(privateKey)).trustManager(File(clientCa)).clientAuth(ClientAuth.REQUIRE).build())
                .addService(ServerInterceptors.intercept(ProfileServiceImpl(users), ClientSanInterceptor(allowedSans)))
            log.info("Configured mTLS for gRPC server")
        } else {
            builder.addService(ProfileServiceImpl(users))
            log.info("Configured insecure gRPC server (no certificates provided)")
        }
    }

    private val server = (if (reflection) builder.addService(ProtoReflectionService.newInstance()) else builder).build()
    fun start() { server.start(); log.info("Profile gRPC server started on port {}", port); Runtime.getRuntime().addShutdownHook(Thread { server.shutdown() }) }
}
