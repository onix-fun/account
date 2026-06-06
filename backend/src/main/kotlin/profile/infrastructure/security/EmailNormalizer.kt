package profile.infrastructure.security

import profile.shared.ApiErrorCode
import profile.shared.apiError
import java.util.Locale

object EmailNormalizer {
    private val pattern = Regex("^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$", RegexOption.IGNORE_CASE)

    fun normalize(value: String, field: String = "email"): String {
        val email = value.trim().lowercase(Locale.ROOT)
        if (email.length !in 3..254 || email.any { it.code < 32 || it == '\u007f' } || !pattern.matches(email)) {
            apiError(ApiErrorCode.VALIDATION_INVALID_EMAIL, field)
        }
        return email
    }
}
