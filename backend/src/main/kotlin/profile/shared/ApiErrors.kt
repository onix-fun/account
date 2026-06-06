package profile.shared

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

enum class ApiErrorCode(
    val numericCode: Int,
    val message: String,
    val status: HttpStatusCode
) {
    VALIDATION_INVALID_REQUEST(1000, "Invalid request", HttpStatusCode.BadRequest),
    VALIDATION_REQUIRED_FIELD(1001, "Required field is missing", HttpStatusCode.BadRequest),
    VALIDATION_INVALID_EMAIL(1002, "Invalid email format", HttpStatusCode.BadRequest),
    VALIDATION_USERNAME_TOO_SHORT(1003, "Username must be at least 3 characters", HttpStatusCode.BadRequest),
    VALIDATION_PASSWORD_TOO_SHORT(1004, "Password must be at least 8 characters", HttpStatusCode.BadRequest),
    VALIDATION_INVALID_CODE(1005, "Verification code must be 6 digits", HttpStatusCode.BadRequest),
    VALIDATION_INVALID_UUID(1006, "Invalid UUID format", HttpStatusCode.BadRequest),
    VALIDATION_SEARCH_QUERY_TOO_SHORT(1007, "Search query must be at least 2 characters", HttpStatusCode.BadRequest),

    AUTH_ACCOUNT_NOT_FOUND(2000, "Account not found", HttpStatusCode.NotFound),
    AUTH_INVALID_CREDENTIALS(2001, "Invalid credentials", HttpStatusCode.Unauthorized),
    AUTH_INVALID_PASSWORD(2002, "Invalid password", HttpStatusCode.Unauthorized),
    AUTH_ACCOUNT_BLOCKED(2003, "Account is blocked", HttpStatusCode.Forbidden),
    AUTH_EMAIL_NOT_VERIFIED(2004, "Email is not verified", HttpStatusCode.Forbidden),
    AUTH_EMAIL_IN_USE(2005, "Email is already in use", HttpStatusCode.Conflict),
    AUTH_USERNAME_IN_USE(2006, "Username is already in use", HttpStatusCode.Conflict),
    AUTH_REGISTRATION_PENDING(2007, "Registration is already pending", HttpStatusCode.Conflict),
    AUTH_PENDING_REGISTRATION_NOT_FOUND(2008, "Pending registration not found", HttpStatusCode.NotFound),
    AUTH_INVALID_OR_EXPIRED_CODE(2009, "Invalid or expired verification code", HttpStatusCode.BadRequest),
    AUTH_SESSION_NOT_FOUND(2010, "Account session not found", HttpStatusCode.NotFound),
    AUTH_INVALID_REFRESH_TOKEN(2011, "Invalid refresh token", HttpStatusCode.Unauthorized),
    AUTH_SESSION_REVOKED(2012, "Session is revoked", HttpStatusCode.Unauthorized),
    AUTH_SESSION_EXPIRED(2013, "Session is expired", HttpStatusCode.Unauthorized),
    AUTH_UNAUTHORIZED(2014, "Unauthorized", HttpStatusCode.Unauthorized),
    AUTH_FORBIDDEN(2015, "Forbidden", HttpStatusCode.Forbidden),
    AUTH_CODE_LOCKED(2016, "Verification code is locked", HttpStatusCode.TooManyRequests),
    AUTH_CODE_RESEND_TOO_SOON(2017, "Verification code was sent too recently", HttpStatusCode.TooManyRequests),
    AUTH_EMAIL_CHANGE_NOT_FOUND(2018, "Pending email change not found", HttpStatusCode.NotFound),

    USER_NOT_FOUND(3000, "User not found", HttpStatusCode.NotFound),
    AVATAR_FILE_REQUIRED(3001, "Avatar file is required", HttpStatusCode.BadRequest),
    AVATAR_CONTENT_TYPE_REQUIRED(3002, "Avatar content type is required", HttpStatusCode.BadRequest),
    AVATAR_UNSUPPORTED_TYPE(3003, "Avatar type is not supported", HttpStatusCode.UnsupportedMediaType),
    AVATAR_TOO_LARGE(3004, "Avatar must be 5MB or smaller", HttpStatusCode.PayloadTooLarge),
    AVATAR_SIGNATURE_MISMATCH(3005, "Avatar signature does not match content type", HttpStatusCode.UnsupportedMediaType),
    AVATAR_INVALID_IMAGE(3006, "Avatar is not a valid image", HttpStatusCode.UnsupportedMediaType),
    AVATAR_DIMENSIONS_TOO_LARGE(3007, "Avatar dimensions are too large", HttpStatusCode.PayloadTooLarge),

    SESSION_NOT_FOUND(4000, "Session not found", HttpStatusCode.NotFound),
    SESSION_REVOKE_FORBIDDEN(4001, "Session cannot be revoked by this user", HttpStatusCode.Forbidden),

    INFRASTRUCTURE_INTERNAL_ERROR(5000, "Internal server error", HttpStatusCode.InternalServerError),
    INFRASTRUCTURE_SERVICE_UNAVAILABLE(5001, "Service temporarily unavailable", HttpStatusCode.ServiceUnavailable),
    INFRASTRUCTURE_RATE_LIMITED(5002, "Too many requests", HttpStatusCode.TooManyRequests),
    SECURITY_CSRF_INVALID(5100, "Valid CSRF token is required", HttpStatusCode.Forbidden),
    SECURITY_ORIGIN_INVALID(5101, "Trusted Origin header is required", HttpStatusCode.Forbidden),
    SECURITY_TOKEN_INVALID(5102, "Invalid bearer token", HttpStatusCode.Unauthorized)
}

@Serializable
data class ApiFieldError(
    val field: String,
    val code: String,
    val numericCode: Int
)

@Serializable
data class ApiErrorResponse(
    val code: String,
    val numericCode: Int,
    val message: String,
    val fieldErrors: List<ApiFieldError> = emptyList()
)

class ApiException(
    val error: ApiErrorCode,
    val fields: List<String> = emptyList(),
    cause: Throwable? = null
) : RuntimeException(error.message, cause)

fun apiError(error: ApiErrorCode, vararg fields: String): Nothing {
    throw ApiException(error, fields.toList())
}
