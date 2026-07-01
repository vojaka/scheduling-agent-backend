package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.ProviderKey;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class OneOffPaymentRequest {
    /** Optional — falls back to the configured default provider. */
    private ProviderKey provider;
    /** Bubble order id this payment settles. */
    private String orderId;
    /** Company scope (Bubble company text id). */
    private String companyId;
    private long amountMinor;
    /** ISO-4217, e.g. "EUR". */
    private String currency;
    /** Provider method key, or null for default/all. */
    private String method;
    private CustomerInfo customer;
    /** Where the shopper returns after the hosted payment page. */
    private String returnUrl;
    private Map<String, Object> metadata;
}
