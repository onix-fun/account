package profile.infrastructure.ratelimit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import profile.infrastructure.redis.RedisManager
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
    val redisManager = application.inject<RedisManager>().value
    val rules = pluginConfig.rules

    onCall { call ->
        val requestPath = call.request.path().substringBefore(";")
        val matchedRule = rules.entries.firstOrNull { (pattern, _) ->
            requestPath.startsWith(pattern)
        }?.value

        if (matchedRule != null) {
            val clientIp = call.request.local.remoteHost
                ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                ?: "unknown"
            val allowed = redisManager.checkRateLimit(
                scope = "ratelimit:${matchedRule.hashCode()}",
                key = clientIp,
                max = matchedRule.max,
                windowSeconds = matchedRule.windowSeconds
            )
            if (!allowed) {
                call.respond(
                    HttpStatusCode(429, "Too Many Requests"),
                    ApiErrorResponse(
                        code = "INFRASTRUCTURE_RATE_LIMITED",
                        numericCode = 5002,
                        message = "Too many requests",
                        fieldErrors = emptyList()
                    )
                )
            }
        }
    }
}
