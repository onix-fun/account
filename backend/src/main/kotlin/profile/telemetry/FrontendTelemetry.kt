package profile.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class FrontendTelemetryBatch(val events: List<FrontendTelemetryEvent>)

@Serializable
data class FrontendTelemetryEvent(
    val type: String,
    val route: String,
    val name: String,
    val value: Double? = null
) {
    fun validate() {
        require(type in setOf("error", "web_vital"))
        require(route.length in 1..200 && !route.contains("?"))
        require(name.length in 1..100)
        require(value == null || value.isFinite())
    }
}
