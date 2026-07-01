package com.comforthub.backoffice.health;

import com.comforthub.backoffice.client.BubbleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator for the Bubble Data API connection (issue #3).
 *
 * <p>Delegates to {@link BubbleClient#ping()}, which reuses the same
 * RestClient instance and {@code addAuthHeader}/token setup as every other
 * Bubble call in this app, rather than opening a second HTTP client just for
 * health polling. That means this indicator exercises both network
 * reachability <em>and</em> Bubble token validity in a single lightweight
 * call.
 *
 * <p><b>VERIFIED:</b> {@code GET /user?limit=1} matches the issue's own
 * suggestion ("ping the /obj/user endpoint") and mirrors
 * {@code BubbleClient.fetchUsers()}'s existing {@code /user} resource — the
 * configured {@code bubble.api.base-url} already ends in {@code /obj} (see
 * application.properties), so the effective request is
 * {@code GET .../obj/user?limit=1}.
 *
 * <p>Never throws: any failure (network, 401/403 auth, timeout, etc.) is
 * caught and reported as {@link Health#down()} with the exception message as
 * a detail, so a Bubble outage degrades this indicator instead of breaking
 * {@code /actuator/health} itself.
 */
@Component
public class BubbleHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(BubbleHealthIndicator.class);

    private final BubbleClient bubbleClient;

    public BubbleHealthIndicator(BubbleClient bubbleClient) {
        this.bubbleClient = bubbleClient;
    }

    @Override
    public Health health() {
        try {
            bubbleClient.ping();
            return Health.up()
                    .withDetail("service", "Bubble Data API")
                    .withDetail("check", "GET /user?limit=1")
                    .build();
        } catch (Exception e) {
            log.warn("Bubble health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("service", "Bubble Data API")
                    .withDetail("check", "GET /user?limit=1")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
