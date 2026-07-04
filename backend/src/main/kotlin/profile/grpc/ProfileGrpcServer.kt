package profile.grpc

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import profile.infrastructure.db.UserRepository
import profile.v1.GetUserByUsernameRequest
import profile.v1.GetUserByIdRequest
import profile.v1.ProfileServiceGrpc
import profile.v1.UserRef

class ProfileServiceImpl(private val users: UserRepository) : ProfileServiceGrpc.ProfileServiceImplBase() {
    override fun getUserByUsername(request: GetUserByUsernameRequest, observer: StreamObserver<UserRef>) {
        val user = request.username.takeIf { it.isNotBlank() }?.let(users::findByUsername)
        if (user == null) return observer.onError(StatusException(Status.NOT_FOUND))
        observer.onNext(UserRef.newBuilder().setId(user.id).setUsername(user.username).build()); observer.onCompleted()
    }

    override fun getUserById(request: GetUserByIdRequest, observer: StreamObserver<UserRef>) {
        val user = request.id.takeIf { it.isNotBlank() }?.let(users::findById)
        if (user == null) return observer.onError(StatusException(Status.NOT_FOUND))
        observer.onNext(UserRef.newBuilder().setId(user.id).setUsername(user.username).build()); observer.onCompleted()
    }
}
