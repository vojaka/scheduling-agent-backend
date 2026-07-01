package com.comforthub.backoffice.payment;

import com.comforthub.backoffice.model.entity.PaymentEntity;
import com.comforthub.backoffice.payment.dto.*;
import com.comforthub.backoffice.payment.everypay.EveryPayClient;
import com.comforthub.backoffice.payment.everypay.EveryPayPaymentProvider;
import com.comforthub.backoffice.payment.montonio.MontonioClient;
import com.comforthub.backoffice.payment.montonio.MontonioPaymentProvider;
import com.comforthub.backoffice.repository.PaymentRepository;
import com.comforthub.backoffice.service.CompanyCredentialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProviderCredentialsTest {

    @Mock
    private MontonioClient montonioClient;

    @Mock
    private EveryPayClient everyPayClient;

    @Mock
    private CompanyCredentialService credentialService;

    @Mock
    private PaymentRepository paymentRepository;

    private MontonioPaymentProvider montonioProvider;
    private EveryPayPaymentProvider everyPayProvider;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        montonioProvider = new MontonioPaymentProvider(montonioClient, objectMapper, paymentRepository);
        everyPayProvider = new EveryPayPaymentProvider(everyPayClient, objectMapper, credentialService, paymentRepository);
    }

    @Test
    void montonio_createOneOff_passesCompanyIdToClient() {
        OneOffPaymentRequest request = OneOffPaymentRequest.builder()
                .orderId("order-1")
                .amountMinor(1000L)
                .currency("EUR")
                .companyId("company-1")
                .build();

        when(montonioClient.postOrder(any(), eq("company-1")))
                .thenReturn(Collections.singletonMap("uuid", "montonio-ref-1"));

        PaymentSession session = montonioProvider.createOneOff(request);

        assertThat(session.getProviderRef()).isEqualTo("montonio-ref-1");
        verify(montonioClient).postOrder(any(), eq("company-1"));
    }

    @Test
    void montonio_refund_resolvesCompanyIdFromRepository() {
        RefundRequest request = RefundRequest.builder()
                .providerRef("montonio-ref-1")
                .amountMinor(500L)
                .build();

        PaymentEntity payment = new PaymentEntity();
        payment.setCompanyId("company-1");
        when(paymentRepository.findByProviderRef("montonio-ref-1")).thenReturn(Optional.of(payment));

        when(montonioClient.postRefund(any(), eq("company-1")))
                .thenReturn(Collections.singletonMap("uuid", "refund-ref-1"));

        montonioProvider.refund(request);

        verify(montonioClient).postRefund(any(), eq("company-1"));
    }

    @Test
    void everypay_createOneOff_passesCompanyIdToClient() {
        OneOffPaymentRequest request = OneOffPaymentRequest.builder()
                .orderId("order-1")
                .amountMinor(1000L)
                .currency("EUR")
                .companyId("company-1")
                .build();

        when(everyPayClient.config()).thenReturn(new com.comforthub.backoffice.payment.config.PaymentProperties.Everypay());
        when(everyPayClient.post(eq("/payments/oneoff"), any(), eq("company-1")))
                .thenReturn(Map.of("payment_reference", "everypay-ref-1", "payment_link", "https://redirect"));

        PaymentSession session = everyPayProvider.createOneOff(request);

        assertThat(session.getProviderRef()).isEqualTo("everypay-ref-1");
        verify(everyPayClient).post(eq("/payments/oneoff"), any(), eq("company-1"));
    }

    @Test
    void everypay_refund_resolvesCompanyIdFromRepository() {
        RefundRequest request = RefundRequest.builder()
                .providerRef("everypay-ref-1")
                .amountMinor(500L)
                .build();

        PaymentEntity payment = new PaymentEntity();
        payment.setCompanyId("company-1");
        when(paymentRepository.findByProviderRef("everypay-ref-1")).thenReturn(Optional.of(payment));
        when(everyPayClient.config()).thenReturn(new com.comforthub.backoffice.payment.config.PaymentProperties.Everypay());

        when(everyPayClient.post(eq("/payments/everypay-ref-1/refund"), any(), eq("company-1")))
                .thenReturn(Collections.singletonMap("payment_reference", "refund-ref-1"));

        everyPayProvider.refund(request);

        verify(everyPayClient).post(eq("/payments/everypay-ref-1/refund"), any(), eq("company-1"));
    }
}
