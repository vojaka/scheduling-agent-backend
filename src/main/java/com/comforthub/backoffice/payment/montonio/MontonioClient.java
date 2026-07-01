package com.comforthub.backoffice.payment.montonio;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.comforthub.backoffice.payment.PaymentException;
import com.comforthub.backoffice.payment.config.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin HTTP client for the Montonio Stargate API.
 *
 * <p>Auth model: request data is packed into a JWT signed with the store Secret
 * Key (HMAC-SHA256) and carrying the Access Key. Inbound webhooks are verified
 * the same way.
 *
 * <p>NOTE: exact request/response envelopes (e.g. the {@code {\"data\": <jwt>}}
 * wrapper and refund path) must be confirmed against the Montonio sandbox before
 * go-live (issue #81). The signing + verification logic here is the stable part.
 */
@Component
public class MontonioClient {

    private static final Logger log = LoggerFactory.getLogger(MontonioClient.class);

    private final RestClient restClient;
    private final PaymentProperties.Montonio config;

    public MontonioClient(RestClient.Builder builder, PaymentProperties properties) {
        this.config = properties.getMontonio();
        this.restClient = builder
                .baseUrl(config.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    private Algorithm algorithm() {
        if (config.getSecretKey() == null || config.getSecretKey().isBlank()) {
            throw new PaymentException("Montonio secret key is not configured");
        }
        return Algorithm.HMAC256(config.getSecretKey());
    }

    /** Sign an arbitrary claim map into a Montonio request JWT. */
    public String sign(Map<String, Object> payload) {
        return JWT.create()
                .withPayload(payload)
                .withClaim("accessKey", config.getAccessKey())
                .sign(algorithm());
    }

    /** Verify + decode an inbound order token. Throws on invalid signature. */
    public DecodedJWT verify(String token) {
        return JWT.require(algorithm()).build().verify(token);
    }

    /** POST a signed order, returning Montonio's JSON response as a map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> postOrder(Map<String, Object> orderPayload) {
        String jwt = sign(orderPayload);
        try {
            return restClient.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("data", jwt))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("Montonio create order failed: {}", e.getMessage());
            throw new PaymentException("Montonio create order failed: " + e.getMessage(), e);
        }
    }

    /** POST a signed refund request. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> postRefund(Map<String, Object> refundPayload) {
        String jwt = sign(refundPayload);
        try {
            return restClient.post()
                    .uri("/refunds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("data", jwt))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("Montonio refund failed: {}", e.getMessage());
            throw new PaymentException("Montonio refund failed: " + e.getMessage(), e);
        }
    }
}
