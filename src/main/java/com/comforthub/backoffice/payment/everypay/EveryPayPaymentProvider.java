package com.comforthub.backoffice.payment.everypay;

import com.comforthub.backoffice.model.entity.PaymentEntity;
import com.comforthub.backoffice.payment.*;
import com.comforthub.backoffice.payment.dto.*;
import com.comforthub.backoffice.payment.provider.*;
import com.comforthub.backoffice.repository.PaymentRepository;
import com.comforthub.backoffice.service.CompanyCredentialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * EveryPay APIv4 provider. Supports one-off, recurring (CIT/MIT tokenization),
 * refunds, webhooks and disputes.
 */
@Component
public class EveryPayPaymentProvider
        implements OneOffPayments, RecurringPayments, RefundablePayments, WebhookHandling, DisputeAware {

    private static final Logger log = LoggerFactory.getLogger(EveryPayPaymentProvider.class);

    private final EveryPayClient client;
    private final ObjectMapper objectMapper;
    private final CompanyCredentialService credentialService;
    private final PaymentRepository paymentRepository;

    public EveryPayPaymentProvider(EveryPayClient client, ObjectMapper objectMapper,
                                   CompanyCredentialService credentialService, PaymentRepository paymentRepository) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.credentialService = credentialService;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public ProviderKey key() {
        return ProviderKey.EVERYPAY;
    }

    @Override
    public Set<PaymentCapability> capabilities() {
        return EnumSet.of(PaymentCapability.ONE_OFF, PaymentCapability.RECURRING,
                PaymentCapability.REFUND, PaymentCapability.WEBHOOK, PaymentCapability.DISPUTE);
    }

    // --- One-off -----------------------------------------------------------

    @Override
    public PaymentSession createOneOff(OneOffPaymentRequest r) {
        Map<String, Object> body = basePayment(r.getOrderId(), r.getAmountMinor(), r.getCurrency(),
                r.getReturnUrl(), r.getCustomer(), r.getCompanyId());
        return toSession(client.post("/payments/oneoff", body, r.getCompanyId()));
    }

    // --- Recurring ---------------------------------------------------------

    @Override
    public PaymentSession initRecurring(RecurringInitRequest r) {
        Map<String, Object> body = basePayment(r.getOrderId(), r.getAmountMinor(), r.getCurrency(),
                r.getReturnUrl(), r.getCustomer(), r.getCompanyId());
        body.put("request_token", true);
        body.put("token_agreement", agreement(r.getAgreementType()));
        return toSession(client.post("/payments/oneoff", body, r.getCompanyId()));
    }

    @Override
    public PaymentResult chargeRecurring(RecurringChargeRequest r) {
        String companyId = r.getCompanyId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_username", resolveApiUsername(companyId));
        body.put("account_name", resolveAccountName(companyId));
        body.put("amount", minorToMajor(r.getAmountMinor()));
        body.put("order_reference", r.getOrderId());
        body.put("nonce", UUID.randomUUID().toString());
        body.put("timestamp", Instant.now().toString());
        body.put("token", r.getTokenRef());
        body.put("token_agreement", agreement(r.getAgreementType()));
        Map<String, Object> response = client.post("/payments/mit", body, companyId);
        return PaymentResult.builder()
                .providerRef(asString(response.get("payment_reference")))
                .status(mapStatus(asString(response.get("payment_state"))))
                .tokenRef(r.getTokenRef())
                .rawStatus(asString(response.get("payment_state")))
                .build();
    }

    @Override
    public void revokeToken(String tokenRef) {
        log.info("EveryPay token revoke requested for {} (managed via portal/API — issue #85)", tokenRef);
    }

    // --- Refund ------------------------------------------------------------

    @Override
    public RefundResult refund(RefundRequest r) {
        String companyId = resolveCompanyIdFromPaymentRef(r.getProviderRef());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_username", resolveApiUsername(companyId));
        body.put("payment_reference", r.getProviderRef());
        body.put("nonce", UUID.randomUUID().toString());
        body.put("timestamp", Instant.now().toString());
        if (r.getAmountMinor() != null) {
            body.put("amount", minorToMajor(r.getAmountMinor()));
        }
        Map<String, Object> response = client.post("/payments/" + r.getProviderRef() + "/refund", body, companyId);
        return RefundResult.builder()
                .providerRef(r.getProviderRef())
                .refundRef(asString(response.get("payment_reference")))
                .status(mapStatus(asString(response.get("payment_state"))))
                .amountMinor(r.getAmountMinor() == null ? 0 : r.getAmountMinor())
                .build();
    }

    // --- Webhook -----------------------------------------------------------

    @Override
    public PaymentEvent parseWebhook(String rawBody, Map<String, String> headers) {
        String paymentReference = field(rawBody, "payment_reference");
        if (paymentReference == null) {
            return PaymentEvent.builder()
                    .operation(PaymentOperation.ONE_OFF)
                    .status(PaymentStatus.FAILED)
                    .signatureValid(false)
                    .rawPayload(rawBody)
                    .build();
        }
        String companyId = resolveCompanyIdFromPaymentRef(paymentReference);
        Map<String, Object> status = client.get("/payments/" + paymentReference
                + "?api_username=" + resolveApiUsername(companyId), companyId);
        String state = asString(status.get("payment_state"));
        return PaymentEvent.builder()
                .providerRef(paymentReference)
                .providerEventId("everypay:" + paymentReference + ":" + state)
                .operation(PaymentOperation.ONE_OFF)
                .status(mapStatus(state))
                .tokenRef(asString(status.get("token")))
                .signatureValid(true)
                .rawPayload(rawBody)
                .build();
    }

    // --- Dispute -----------------------------------------------------------

    @Override
    public DisputeEvent parseDispute(String rawBody, Map<String, String> headers) {
        return DisputeEvent.builder()
                .providerRef(field(rawBody, "payment_reference"))
                .signatureValid(true)
                .rawPayload(rawBody)
                .build();
    }

    // --- helpers -----------------------------------------------------------

    private String resolveCompanyIdFromPaymentRef(String providerRef) {
        if (providerRef == null) return null;
        return paymentRepository.findByProviderRef(providerRef)
                .map(PaymentEntity::getCompanyId)
                .orElse(null);
    }

    private String resolveApiUsername(String companyId) {
        if (companyId != null) {
            return credentialService.getDecryptedCredential(companyId, "EVERYPAY", "api_username")
                    .orElse(client.config().getUsername());
        }
        return client.config().getUsername();
    }

    private String resolveAccountName(String companyId) {
        if (companyId != null) {
            return credentialService.getDecryptedCredential(companyId, "EVERYPAY", "account_name")
                    .orElse(client.config().getAccountName());
        }
        return client.config().getAccountName();
    }

    private Map<String, Object> basePayment(String orderId, long amountMinor, String currency,
                                            String returnUrl, CustomerInfo customer, String companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_username", resolveApiUsername(companyId));
        body.put("account_name", resolveAccountName(companyId));
        body.put("amount", minorToMajor(amountMinor));
        body.put("order_reference", orderId);
        body.put("nonce", UUID.randomUUID().toString());
        body.put("timestamp", Instant.now().toString());
        body.put("customer_url", returnUrl);
        if (currency != null) {
            body.put("currency", currency);
        }
        if (customer != null) {
            body.put("email", customer.getEmail());
            if (customer.getLocale() != null) {
                body.put("locale", customer.getLocale());
            }
        }
        return body;
    }

    private PaymentSession toSession(Map<String, Object> response) {
        return PaymentSession.builder()
                .providerRef(asString(response.get("payment_reference")))
                .redirectUrl(asString(response.get("payment_link")))
                .status(PaymentStatus.PENDING)
                .build();
    }

    private String field(String rawBody, String name) {
        try {
            Map<?, ?> map = objectMapper.readValue(rawBody, Map.class);
            Object v = map.get(name);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String agreement(AgreementType type) {
        return type == AgreementType.RECURRING ? "recurring" : "unscheduled";
    }

    /**
     * Formats minor units as a fixed 2-decimal major-unit amount string
     * (e.g. 1010 -&gt; "10.10", 7 -&gt; "0.07"), keeping the money path on
     * {@link BigDecimal} end-to-end so no binary {@code double} is ever
     * constructed (see backend issue #102 / epic #79 "Money" requirement).
     *
     * <p><b>Unverified:</b> whether EveryPay's APIv4 expects a fixed-decimal
     * JSON string or a JSON number for {@code amount} has not been confirmed
     * against the sandbox (tracked alongside #82's sandbox wire-shape TODOs).
     * A fixed 2-decimal string is the defensible default for a JSON payment
     * API; revisit once the sandbox contract is confirmed.
     */
    private static String minorToMajor(long amountMinor) {
        return BigDecimal.valueOf(amountMinor, 2).toPlainString();
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static PaymentStatus mapStatus(String state) {
        if (state == null) return PaymentStatus.PENDING;
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "settled", "completed" -> PaymentStatus.PAID;
            case "authorised", "authorized" -> PaymentStatus.AUTHORIZED;
            case "initial", "pending", "waiting_for_3ds_response", "sent_for_processing" -> PaymentStatus.PENDING;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "partially_refunded" -> PaymentStatus.PARTIALLY_REFUNDED;
            case "voided", "abandoned", "cancelled" -> PaymentStatus.CANCELLED;
            case "chargebacked", "chargeback" -> PaymentStatus.CHARGEBACK;
            default -> PaymentStatus.FAILED;
        };
    }
}
