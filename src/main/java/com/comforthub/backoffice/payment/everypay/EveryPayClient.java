package com.comforthub.backoffice.payment.everypay;

import com.comforthub.backoffice.payment.PaymentException;
import com.comforthub.backoffice.payment.config.PaymentProperties;
import com.comforthub.backoffice.service.CompanyCredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Thin HTTP client for the EveryPay APIv4 gateway.
 * Auth: HTTP Basic — API secret as username, blank password.
 */
@Component
public class EveryPayClient {

    private static final Logger log = LoggerFactory.getLogger(EveryPayClient.class);

    private final RestClient restClient;
    private final PaymentProperties.Everypay config;
    private final CompanyCredentialService credentialService;

    public EveryPayClient(RestClient.Builder builder, PaymentProperties properties, CompanyCredentialService credentialService) {
        this.config = properties.getEverypay();
        this.credentialService = credentialService;
        this.restClient = builder
                .baseUrl(config.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    public PaymentProperties.Everypay config() {
        return config;
    }

    private String resolveBasicAuth(String companyId) {
        String username = config.getUsername();
        String secret = config.getSecret();
        if (companyId != null) {
            username = credentialService.getDecryptedCredential(companyId, "EVERYPAY", "api_username")
                    .orElse(config.getUsername());
            secret = credentialService.getDecryptedCredential(companyId, "EVERYPAY", "api_secret")
                    .orElse(config.getSecret());
        }
        return Base64.getEncoder().encodeToString(
                (safe(username) + ":" + safe(secret)).getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String path, Map<String, Object> body, String companyId) {
        String basic = resolveBasicAuth(companyId);
        try {
            return restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("EveryPay POST {} failed: {}", path, e.getMessage());
            throw new PaymentException("EveryPay POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String path, String companyId) {
        String basic = resolveBasicAuth(companyId);
        try {
            return restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("EveryPay GET {} failed: {}", path, e.getMessage());
            throw new PaymentException("EveryPay GET " + path + " failed: " + e.getMessage(), e);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

