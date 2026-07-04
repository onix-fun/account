package profile.infrastructure.ratelimit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import profile.infrastructure.db.RateLimitRepository
import profile.shared.ApiErrorResponse

data class RateLimitRule(
    val max: Long,
    val windowSeconds: Long
)

class RateLimitConfig {
    val rules = mutableMapOf<String, RateLimitRule>()

    fun route(path: String, max: Long, windowSeconds: Long) {
        rules[path] = RateLimitRule(max, windowSeconds)
    }
}

val RateLimit = createApplicationPlugin("RateLimit", ::RateLimitConfig) {
    val rateLimitRepository = application.inject<RateLimitRepository>().value
    val rules = pluginConfig.rules

    onCall { call ->
        val requestPath = call.request.path().substringBefore(";")
        val matchedRule = rules.entries.firstOrNull { (pattern, _) ->
            requestPath.startsWith(pattern)
        }?.value

        if (matchedRule != null) {
            val ipKey = call.request.origin.remoteHost
            val allowed = runCatching {
                rateLimitRepository.checkAndIncrement("ratelimit:${matchedRule.hashCode()}:ip", ipKey, matchedRule.max, matchedRule.windowSeconds)
            }.getOrElse {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ApiErrorResponse("INFRASTRUCTURE_SERVICE_UNAVAILABLE", 5001, "Service temporarily unavailable")
                )
                return@onCall
            }
            if (!allowed) {
                call.response.headers.append(HttpHeaders.RetryAfter, matchedRule.windowSeconds.toString())
                call.respond(
                    HttpStatusCode(429, "Too Many Requests"),
                    ApiErrorResponse(
                        code = "INFRASTRUCTURE_RATE_LIMITED",
                        numericCode = 5002,
                        message = "Too many requests",
                        fieldErrors = emptyList()
                    )
                )
                return@onCall
            }
        }
    }
}
