package com.comforthub.backoffice.payment.everypay;

import com.comforthub.backoffice.payment.PaymentException;
import com.comforthub.backoffice.payment.config.PaymentProperties;
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

    public EveryPayClient(RestClient.Builder builder, PaymentProperties properties) {
        this.config = properties.getEverypay();
        String basic = Base64.getEncoder().encodeToString(
                (safe(config.getUsername()) + ":" + safe(config.getSecret())).getBytes(StandardCharsets.UTF_8));
        this.restClient = builder
                .baseUrl(config.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    public PaymentProperties.Everypay config() {
        return config;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(path)
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
    public Map<String, Object> get(String path) {
        try {
            return restClient.get()
                    .uri(path)
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
