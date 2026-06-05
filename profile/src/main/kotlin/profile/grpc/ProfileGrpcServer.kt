package profile.grpc

import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import io.grpc.protobuf.services.ProtoReflectionService
import org.slf4j.LoggerFactory
import profile.v1.GetUserByUsernameRequest
import profile.v1.ProfileServiceGrpc
import profile.v1.UserRef
import profile.infrastructure.db.UserRepository

class ProfileServiceImpl(
    private val userRepository: UserRepository
) : ProfileServiceGrpc.ProfileServiceImplBase() {

    override fun getUserByUsername(request: GetUserByUsernameRequest, responseObserver: StreamObserver<UserRef>) {
        try {
            val username = request.username
            if (username.isBlank()) {
                responseObserver.onError(
                    StatusException(Status.INVALID_ARGUMENT.withDescription("Username cannot be blank"))
                )
                return
            }
            val user = userRepository.findByUsername(username)
            if (user == null) {
                responseObserver.onError(
                    StatusException(Status.NOT_FOUND.withDescription("User not found: $username"))
                )
                return
            }
            val ref = UserRef.newBuilder()
                .setId(user.id)
                .setUsername(user.username)
                .build()
            responseObserver.onNext(ref)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(
                StatusException(Status.INTERNAL.withDescription(e.message).withCause(e))
            )
        }
    }
}

class ProfileGrpcServer(
    private val userRepository: UserRepository,
    private val port: Int
) {
    private val log = LoggerFactory.getLogger(ProfileGrpcServer::class.java)

    private val server = ServerBuilder.forPort(port)
        .addService(ProfileServiceImpl(userRepository))
        .addService(ProtoReflectionService.newInstance())
        .build()

    fun start() {
        server.start()
        log.info("Profile gRPC server started on port $port")
        Runtime.getRuntime().addShutdownHook(Thread {
            server.shutdown()
            log.info("Profile gRPC server shut down")
        })
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }
}
