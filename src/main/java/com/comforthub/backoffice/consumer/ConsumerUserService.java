package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the authenticated principal (Auth0 JWT {@code sub}) to the <b>live
 * Bubble user record</b> — the scoping key of the consumer API.
 *
 * <p>This deliberately differs from the backoffice's
 * {@link com.comforthub.backoffice.service.CurrentUserService}, which resolves
 * against the hourly-synced {@code bubble_users} PostgreSQL mirror and scopes
 * by <i>company</i>. Consumers (role {@code Client}) sign up in real time from
 * the mobile app, so waiting for the ETL would break first-session flows;
 * instead the user is looked up straight from the Bubble Data API by the
 * {@code "Auth - Auth0 - sub"} field, and everything is scoped by that user's
 * id (their cart, orders, addresses, profile).
 */
@Service
public class ConsumerUserService {

    /** Bubble Data API object type for users. */
    public static final String USER_TYPE = "user";

    /** User field holding the Auth0 {@code sub} (confirmed in comforthub_schema.md). */
    static final String F_AUTH0_SUB = "Auth - Auth0 - sub";

    /** User field referencing the user's single cart. */
    static final String F_CART = "Cart (single)";

    private final BubbleClient bubbleClient;
    private final ObjectMapper objectMapper;

    public ConsumerUserService(BubbleClient bubbleClient, ObjectMapper objectMapper) {
        this.bubbleClient = bubbleClient;
        this.objectMapper = objectMapper;
    }

    /** The Auth0 {@code sub} claim of the current request, or empty if unauthenticated. */
    public Optional<String> currentSub() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.ofNullable(jwtAuth.getToken().getSubject());
        }
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject());
        }
        return Optional.empty();
    }

    /**
     * The current user's live Bubble record, looked up by
     * {@code "Auth - Auth0 - sub" = sub}. Empty when unauthenticated or when no
     * Bubble user carries this sub (e.g. signup not completed in Bubble yet).
     */
    public Optional<Map<String, Object>> currentBubbleUser() {
        return currentSub().flatMap(sub -> {
            List<Map<String, Object>> results = bubbleClient
                    .list(USER_TYPE, subConstraint(sub), 0, 1)
                    .getResults();
            return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0));
        });
    }

    /** The current user's Bubble id — the consumer-API scoping key. */
    public Optional<String> currentBubbleUserId() {
        return currentBubbleUser().map(u -> asString(u.get("_id")));
    }

    /** The current user's Bubble cart id ({@code "Cart (single)"}), if any. */
    public Optional<String> currentCartId() {
        return currentBubbleUser().map(u -> asString(u.get(F_CART)));
    }

    private String subConstraint(String sub) {
        try {
            return objectMapper.writeValueAsString(List.of(
                    Map.of("key", F_AUTH0_SUB, "constraint_type", "equals", "value", sub)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build Bubble constraints", e);
        }
    }

    private static String asString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }
}
