package com.comforthub.backoffice.payment.provider;

import com.comforthub.backoffice.payment.dto.RefundRequest;
import com.comforthub.backoffice.payment.dto.RefundResult;

public interface RefundablePayments extends PaymentProvider {
    RefundResult refund(RefundRequest request);
}
