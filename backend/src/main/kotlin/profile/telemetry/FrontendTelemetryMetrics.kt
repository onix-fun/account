package profile.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes

object FrontendTelemetryMetrics {
    private val meter = GlobalOpenTelemetry.getMeter("profile.frontend.telemetry")
    private val errors = meter.counterBuilder("frontend.errors").setDescription("Frontend errors").build()
    private val webVitals = meter.histogramBuilder("frontend.web_vital")
        .setDescription("Frontend Web Vitals")
        .setUnit("ms")
        .build()

    fun record(event: FrontendTelemetryEvent) {
        val attributes = Attributes.builder()
            .put("frontend.route", event.route)
            .put("frontend.name", event.name)
            .build()

        when (event.type) {
            "error" -> errors.add(1, attributes)
            "web_vital" -> event.value?.let { webVitals.record(it, attributes) }
        }
    }
}
