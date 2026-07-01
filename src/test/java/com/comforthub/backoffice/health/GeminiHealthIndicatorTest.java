package com.comforthub.backoffice.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Uses {@link MockRestServiceServer#bindTo(RestClient.Builder)} (Spring
 * Framework 6.1+, matches this project's Spring Boot 3.2.2 parent) to mock
 * the HTTP layer instead of hitting the real Gemini endpoint.
 */
class GeminiHealthIndicatorTest {

    @Test
    void keyNotConfigured_reportsUnknown_withoutMakingARequest() {
        RestClient.Builder builder = RestClient.builder();
        GeminiHealthIndicator indicator = new GeminiHealthIndicator(builder);
        ReflectionTestUtils.setField(indicator, "geminiApiKey", "");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("reason", "GEMINI_API_KEY not configured");
    }

    @Test
    void placeholderKey_reportsUnknown_withoutMakingARequest() {
        RestClient.Builder builder = RestClient.builder();
        GeminiHealthIndicator indicator = new GeminiHealthIndicator(builder);
        ReflectionTestUtils.setField(indicator, "geminiApiKey", "default-gemini-key");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void validKey_listModelsSucceeds_reportsUp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiHealthIndicator indicator = new GeminiHealthIndicator(builder);
        ReflectionTestUtils.setField(indicator, "geminiApiKey", "test-key");

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=test-key"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        server.verify();
    }

    @Test
    void invalidKey_listModelsFails_reportsDown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiHealthIndicator indicator = new GeminiHealthIndicator(builder);
        ReflectionTestUtils.setField(indicator, "geminiApiKey", "bad-key");

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=bad-key"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        server.verify();
    }
}
