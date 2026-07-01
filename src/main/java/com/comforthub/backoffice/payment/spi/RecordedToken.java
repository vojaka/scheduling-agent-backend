package com.comforthub.backoffice.payment.spi;

import com.comforthub.backoffice.payment.AgreementType;
import com.comforthub.backoffice.payment.ProviderKey;

/** Immutable carrier for a stored payment token. Never contains a PAN. */
public record RecordedToken(
        ProviderKey provider,
        String companyId,
        String customerRef,
        String tokenRef,
        AgreementType agreementType,
        String cardBrand,
        String maskedPan
) {
}
