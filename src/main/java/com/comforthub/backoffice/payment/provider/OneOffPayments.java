package com.comforthub.backoffice.payment.provider;

import com.comforthub.backoffice.payment.dto.OneOffPaymentRequest;
import com.comforthub.backoffice.payment.dto.PaymentSession;

public interface OneOffPayments extends PaymentProvider {
    PaymentSession createOneOff(OneOffPaymentRequest request);
}
