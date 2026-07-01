package com.comforthub.backoffice.payment.montonio;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.comforthub.backoffice.model.entity.PaymentEntity;
import com.comforthub.backoffice.payment.*;
import com.comforthub.backoffice.payment.dto.*;
import com.comforthub.backoffice.payment.provider.*;
import com.comforthub.backoffice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Montonio Stargate provider. Supports one-off, recurring (card token),
 * refunds, webhooks and disputes.
 */
@Component
public class MontonioPaymentProvider
        implements OneOffPayments, RecurringPayments, RefundablePayments, WebhookHandling, DisputeAware {

    private static final Logger log = LoggerFactory.getLogger(MontonioPaymentProvider.class);

    private final MontonioClient client;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;

    public MontonioPaymentProvider(MontonioClient client, ObjectMapper objectMapper, PaymentRepository paymentRepository) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public ProviderKey key() {
        return ProviderKey.MONTONIO;
    }

    @Override
    public Set<PaymentCapability> capabilities() {
        return EnumSet.of(PaymentCapability.ONE_OFF, PaymentCapability.RECURRING,
                PaymentCapability.REFUND, PaymentCapability.WEBHOOK, PaymentCapability.DISPUTE);
    }

    // --- One-off -----------------------------------------------------------

    @Override
    public PaymentSession createOneOff(OneOffPaymentRequest r) {
        Map<String, Object> order = baseOrder(r.getOrderId(), r.getAmountMinor(), r.getCurrency(),
                r.getReturnUrl(), r.getCustomer(), r.getMethod());
        return toSession(client.postOrder(order, r.getCompanyId()));
    }

    // --- Recurring ---------------------------------------------------------

    @Override
    public PaymentSession initRecurring(RecurringInitRequest r) {
        Map<String, Object> order = baseOrder(r.getOrderId(), r.getAmountMinor(), r.getCurrency(),
                r.getReturnUrl(), r.getCustomer(), r.getMethod());
        order.put("requestToken", true);
        return toSession(client.postOrder(order, r.getCompanyId()));
    }

    @Override
    public PaymentResult chargeRecurring(RecurringChargeRequest r) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("merchantReference", r.getOrderId());
        order.put("grandTotal", minorToMajor(r.getAmountMinor()));
        order.put("currency", r.getCurrency());
        order.put("token", r.getTokenRef());
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("method", "cardPayments");
        order.put("payment", payment);
        Map<String, Object> response = client.postOrder(order, r.getCompanyId());
        return PaymentResult.builder()
                .providerRef(asString(response.get("uuid")))
                .status(mapStatus(asString(response.get("paymentStatus"))))
                .tokenRef(r.getTokenRef())
                .rawStatus(asString(response.get("paymentStatus")))
                .build();
    }

    @Override
    public void revokeToken(String tokenRef) {
        log.info("Montonio token revoke requested for {} (no-op until API confirmed)", tokenRef);
    }

    // --- Refund ------------------------------------------------------------

    @Override
    public RefundResult refund(RefundRequest r) {
        String companyId = resolveCompanyId(r.getProviderRef());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderUuid", r.getProviderRef());
        if (r.getAmountMinor() != null) {
            payload.put("amount", minorToMajor(r.getAmountMinor()));
        }
        Map<String, Object> response = client.postRefund(payload, companyId);
        return RefundResult.builder()
                .providerRef(r.getProviderRef())
                .refundRef(asString(response.get("uuid")))
                .status(mapStatus(asString(response.get("status"))))
                .amountMinor(r.getAmountMinor() == null ? 0 : r.getAmountMinor())
                .build();
    }

    // --- Webhook -----------------------------------------------------------

    @Override
    public PaymentEvent parseWebhook(String rawBody, Map<String, String> headers) {
        String orderToken = extractOrderToken(rawBody);
        
        String companyId = null;
        try {
            DecodedJWT unverified = JWT.decode(orderToken);
            String uuid = unverified.getClaim("uuid").asString();
            if (uuid != null) {
                companyId = resolveCompanyId(uuid);
            }
        } catch (Exception e) {
            log.warn("Failed to pre-decode Montonio webhook token: {}", e.getMessage());
        }

        DecodedJWT decoded;
        try {
            decoded = client.verify(orderToken, companyId);
        } catch (Exception e) {
            log.warn("Montonio webhook signature invalid: {}", e.getMessage());
            return PaymentEvent.builder()
                    .operation(PaymentOperation.ONE_OFF)
                    .status(PaymentStatus.FAILED)
                    .signatureValid(false)
                    .rawPayload(rawBody)
                    .build();
        }
        String status = claim(decoded, "paymentStatus");
        String uuid = claim(decoded, "uuid");
        return PaymentEvent.builder()
                .providerRef(uuid)
                .providerEventId("montonio:" + uuid + ":" + status)
                .operation(PaymentOperation.ONE_OFF)
                .status(mapStatus(status))
                .signatureValid(true)
                .rawPayload(rawBody)
                .build();
    }

    // --- Dispute -----------------------------------------------------------

    @Override
    public DisputeEvent parseDispute(String rawBody, Map<String, String> headers) {
        return DisputeEvent.builder()
                .signatureValid(true)
                .rawPayload(rawBody)
                .build();
    }

    // --- helpers -----------------------------------------------------------

    private String resolveCompanyId(String providerRef) {
        if (providerRef == null) return null;
        return paymentRepository.findByProviderRef(providerRef)
                .map(PaymentEntity::getCompanyId)
                .orElse(null);
    }


    private Map<String, Object> baseOrder(String orderId, long amountMinor, String currency,
                                          String returnUrl, CustomerInfo customer, String method) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("merchantReference", orderId);
        order.put("returnUrl", returnUrl);
        order.put("grandTotal", minorToMajor(amountMinor));
        order.put("currency", currency);
        if (customer != null) {
            order.put("locale", customer.getLocale() == null ? "en" : customer.getLocale());
        }
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("method", method == null ? "cardPayments" : method);
        payment.put("amount", minorToMajor(amountMinor));
        payment.put("currency", currency);
        order.put("payment", payment);
        return order;
    }

    private PaymentSession toSession(Map<String, Object> response) {
        return PaymentSession.builder()
                .providerRef(asString(response.get("uuid")))
                .redirectUrl(asString(response.get("paymentUrl")))
                .status(PaymentStatus.PENDING)
                .build();
    }

    private String extractOrderToken(String rawBody) {
        try {
            Map<?, ?> map = objectMapper.readValue(rawBody, Map.class);
            Object token = map.get("orderToken");
            return token != null ? token.toString() : rawBody.trim();
        } catch (Exception e) {
            return rawBody.trim();
        }
    }

    private static String claim(DecodedJWT jwt, String name) {
        Claim c = jwt.getClaim(name);
        return c == null || c.isNull() ? null : c.asString();
    }

    private static double minorToMajor(long amountMinor) {
        return amountMinor / 100.0;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static PaymentStatus mapStatus(String montonioStatus) {
        if (montonioStatus == null) return PaymentStatus.PENDING;
        return switch (montonioStatus.toUpperCase(Locale.ROOT)) {
            case "PAID" -> PaymentStatus.PAID;
            case "AUTHORIZED" -> PaymentStatus.AUTHORIZED;
            case "PENDING", "CREATED" -> PaymentStatus.PENDING;
            case "REFUNDED" -> PaymentStatus.REFUNDED;
            case "PARTIALLY_REFUNDED" -> PaymentStatus.PARTIALLY_REFUNDED;
            case "CANCELLED", "ABANDONED", "VOIDED" -> PaymentStatus.CANCELLED;
            case "CHARGEBACK" -> PaymentStatus.CHARGEBACK;
            default -> PaymentStatus.FAILED;
        };
    }
}
