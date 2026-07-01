package com.comforthub.backoffice.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Actuator health indicator for the Gemini API key/connection (issue #3).
 *
 * <p>Uses Gemini's ListModels endpoint ({@code GET /v1beta/models}) as the
 * validation call — a plain read with no generation/prediction cost, unlike
 * {@code generateContent} ({@link com.comforthub.backoffice.service.ScheduleOrchestrationService})
 * or {@code predict} ({@link com.comforthub.backoffice.service.ImageGenerationService}),
 * which are the two Gemini call sites elsewhere in this app and which do burn
 * real quota. That makes it safe to poll on every health check.
 *
 * <p><b>VERIFIED:</b> {@code gemini.api.key} / {@code GEMINI_API_KEY} wiring
 * (application.properties: {@code gemini.api.key=${GEMINI_API_KEY:}}) and the
 * {@code generativelanguage.googleapis.com} host, cross-checked against both
 * existing Gemini call sites above.
 * <p><b>INFERRED:</b> the ListModels endpoint's exact response shape — not
 * exercised against a live Gemini sandbox in this environment. Only the HTTP
 * status of the call is checked (2xx ⇒ up, non-2xx/network error ⇒ down); the
 * response body is intentionally not parsed, so a shape change wouldn't
 * affect this indicator either way.
 */
@Component
public class GeminiHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(GeminiHealthIndicator.class);

    /** Same placeholder sentinel used by ImageGenerationService for "no real key configured". */
    private static final String PLACEHOLDER_KEY = "default-gemini-key";
    private static final String MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final RestClient restClient;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public GeminiHealthIndicator(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public Health health() {
        if (geminiApiKey == null || geminiApiKey.isBlank() || PLACEHOLDER_KEY.equals(geminiApiKey)) {
            // GEMINI_API_KEY is legitimately unset in local/dev/CI — application.properties
            // defaults it to "", and ImageGenerationService falls back to a mock/placeholder
            // image in that same case rather than treating it as an error. Report UNKNOWN
            // (not DOWN) so a merely-unconfigured *optional* key doesn't drag the aggregate
            // /actuator/health status — and any restart policy wired to it — down the way an
            // actually invalid/expired key should. Spring Boot's default status aggregation
            // only escalates to DOWN/OUT_OF_SERVICE; UNKNOWN doesn't override a healthy UP
            // from other indicators.
            return Health.unknown()
                    .withDetail("service", "Gemini API")
                    .withDetail("reason", "GEMINI_API_KEY not configured")
                    .build();
        }
        try {
            // Cheapest possible validation call: ListModels is a plain GET with no
            // generation cost, unlike generateContent/predict — safe to call on every
            // health check poll without burning paid quota.
            restClient.get()
                    .uri(MODELS_URL + "?key={key}", geminiApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
            return Health.up()
                    .withDetail("service", "Gemini API")
                    .withDetail("check", "GET /v1beta/models")
                    .build();
        } catch (Exception e) {
            log.warn("Gemini health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("service", "Gemini API")
                    .withDetail("check", "GET /v1beta/models")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
