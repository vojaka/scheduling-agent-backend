package com.comforthub.backoffice.payment.dto;

import com.comforthub.backoffice.payment.AgreementType;
import com.comforthub.backoffice.payment.ProviderKey;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Merchant-initiated charge (MIT) against a stored token. */
@Data
@Builder
public class RecurringChargeRequest {
    private ProviderKey provider;
    private String orderId;
    private String companyId;
    private long amountMinor;
    private String currency;
    /** Provider token reference stored from the initial CIT payment. */
    private String tokenRef;
    private AgreementType agreementType;
    private Map<String, Object> metadata;
}
