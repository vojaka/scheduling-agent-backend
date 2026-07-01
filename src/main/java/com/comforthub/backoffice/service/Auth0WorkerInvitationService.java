package com.comforthub.backoffice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link WorkerInvitationService} backed by the Auth0 Management API.
 *
 * <p>Activated by configuration only — when {@code auth0.management.domain} /
 * {@code client-id} / {@code client-secret} are unset (the default), every call
 * is a logged no-op and {@code POST /api/users/invite} still creates the pending
 * membership without emailing anyone. When configured, an invite:
 * <ol>
 *   <li>fetches an M2M access token (client-credentials grant) for the
 *       Management API;</li>
 *   <li>looks the user up by email, creating them (with a random password) if
 *       they do not yet exist — needs the {@code create:users} scope;</li>
 *   <li>triggers a change-password email so the invitee sets their own password
 *       (Authentication API {@code /dbconnections/change_password}).</li>
 * </ol>
 *
 * <p>Every step is wrapped so a transient Auth0 failure degrades to "membership
 * created, email not sent" rather than failing the request. This talks to Auth0
 * over plain HTTP via {@link RestClient} (mirroring {@code BubbleClient}); it
 * deliberately avoids pulling in the Auth0 Management SDK.
 */
@Service
public class Auth0WorkerInvitationService implements WorkerInvitationService {

    private static final Logger log = LoggerFactory.getLogger(Auth0WorkerInvitationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final String domain;
    private final String clientId;
    private final String clientSecret;
    private final String connection;

    public Auth0WorkerInvitationService(
            RestClient.Builder restClientBuilder,
            @Value("${auth0.management.domain:}") String domain,
            @Value("${auth0.management.client-id:}") String clientId,
            @Value("${auth0.management.client-secret:}") String clientSecret,
            @Value("${auth0.management.connection:Username-Password-Authentication}") String connection) {
        this.restClient = restClientBuilder
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        this.domain = normalizeDomain(domain);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.connection = connection;
    }

    private boolean isConfigured() {
        return isSet(domain) && isSet(clientId) && isSet(clientSecret);
    }

    @Override
    public Optional<String> invite(String email, String name, String companyId) {
        if (!isConfigured()) {
            log.warn("Auth0 Management not configured — skipping email invite for {} (company {}). "
                    + "Set auth0.management.domain/client-id/client-secret to enable.", email, companyId);
            return Optional.empty();
        }
        try {
            String token = fetchManagementToken();
            String userId = findOrCreateUser(token, email, name);
            sendChangePasswordEmail(email);
            log.info("Sent Auth0 account-setup invite to {} (auth0 user {})", email, userId);
            return Optional.ofNullable(userId);
        } catch (Exception e) {
            log.warn("Auth0 invite dispatch failed for {} — membership will still be created: {}",
                    email, e.getMessage());
            return Optional.empty();
        }
    }

    private String fetchManagementToken() {
        Map<String, Object> body = Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret,
                "audience", "https://" + domain + "/api/v2/");
        Map<?, ?> response = restClient.post()
                .uri("https://{domain}/oauth/token", domain)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        Object token = response == null ? null : response.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Auth0 token endpoint returned no access_token");
        }
        return token.toString();
    }

    @SuppressWarnings("unchecked")
    private String findOrCreateUser(String token, String email, String name) {
        List<Map<String, Object>> existing = restClient.get()
                .uri("https://{domain}/api/v2/users-by-email?email={email}", domain, email)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(List.class);
        if (existing != null && !existing.isEmpty()) {
            Object id = existing.get(0).get("user_id");
            return id == null ? null : id.toString();
        }

        Map<String, Object> createBody = Map.of(
                "email", email,
                "name", name == null ? email : name,
                "connection", connection,
                "password", randomPassword(),
                "email_verified", false,
                "verify_email", false);
        Map<?, ?> created = restClient.post()
                .uri("https://{domain}/api/v2/users", domain)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .body(Map.class);
        Object id = created == null ? null : created.get("user_id");
        return id == null ? null : id.toString();
    }

    private void sendChangePasswordEmail(String email) {
        Map<String, Object> body = Map.of(
                "client_id", clientId,
                "email", email,
                "connection", connection);
        restClient.post()
                .uri("https://{domain}/dbconnections/change_password", domain)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private static String randomPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        // Mixed case + digits + symbol keeps Auth0's default password policy happy.
        return "Aa1!" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeDomain(String raw) {
        if (raw == null) {
            return "";
        }
        String d = raw.trim();
        if (d.startsWith("https://")) {
            d = d.substring("https://".length());
        }
        if (d.startsWith("http://")) {
            d = d.substring("http://".length());
        }
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}
