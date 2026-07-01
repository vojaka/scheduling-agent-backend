package com.comforthub.backoffice.payment.montonio;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.comforthub.backoffice.payment.PaymentException;
import com.comforthub.backoffice.payment.config.PaymentProperties;
import com.comforthub.backoffice.service.CompanyCredentialService;
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
 */
@Component
public class MontonioClient {

    private static final Logger log = LoggerFactory.getLogger(MontonioClient.class);

    private final RestClient restClient;
    private final PaymentProperties.Montonio config;
    private final CompanyCredentialService credentialService;

    public MontonioClient(RestClient.Builder builder, PaymentProperties properties, CompanyCredentialService credentialService) {
        this.config = properties.getMontonio();
        this.credentialService = credentialService;
        this.restClient = builder
                .baseUrl(config.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    private String resolveAccessKey(String companyId) {
        if (companyId != null) {
            return credentialService.getDecryptedCredential(companyId, "MONTONIO", "access_key")
                    .orElse(config.getAccessKey());
        }
        return config.getAccessKey();
    }

    private Algorithm algorithm(String companyId) {
        String secretKey = config.getSecretKey();
        if (companyId != null) {
            secretKey = credentialService.getDecryptedCredential(companyId, "MONTONIO", "secret_key")
                    .orElse(config.getSecretKey());
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentException("Montonio secret key is not configured");
        }
        return Algorithm.HMAC256(secretKey);
    }

    /** Sign an arbitrary claim map into a Montonio request JWT. */
    public String sign(Map<String, Object> payload, String companyId) {
        return JWT.create()
                .withPayload(payload)
                .withClaim("accessKey", resolveAccessKey(companyId))
                .sign(algorithm(companyId));
    }

    /** Verify + decode an inbound order token. Throws on invalid signature. */
    public DecodedJWT verify(String token, String companyId) {
        return JWT.require(algorithm(companyId)).build().verify(token);
    }

    /** POST a signed order, returning Montonio's JSON response as a map. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> postOrder(Map<String, Object> orderPayload, String companyId) {
        String jwt = sign(orderPayload, companyId);
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
    public Map<String, Object> postRefund(Map<String, Object> refundPayload, String companyId) {
        String jwt = sign(refundPayload, companyId);
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

