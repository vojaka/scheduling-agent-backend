package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.AgreementType;
import com.comforthub.backoffice.payment.ProviderKey;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Customer-present initial payment (CIT) that captures consent + a reusable token. */
@Data
@Builder
public class RecurringInitRequest {
    private ProviderKey provider;
    private String orderId;
    private String companyId;
    private long amountMinor;
    private String currency;
    private String method;
    private CustomerInfo customer;
    private String returnUrl;
    /** Reference for the customer whose card is being tokenized. */
    private String customerRef;
    private AgreementType agreementType;
    private Map<String, Object> metadata;
}
