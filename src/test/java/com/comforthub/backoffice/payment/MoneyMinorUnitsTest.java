package com.comforthub.backoffice.payment;

import com.comforthub.backoffice.payment.dto.OneOffPaymentRequest;
import com.comforthub.backoffice.payment.dto.RecurringChargeRequest;
import com.comforthub.backoffice.payment.dto.RefundRequest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for backend issue #102: provider request payloads must
 * carry exact minor-unit-derived amounts (BigDecimal fixed 2-decimal string),
 * never a binary {@code double} built via {@code amountMinor / 100.0}.
 *
 * <p>The exact wire format (string vs. JSON number) is a defensible default,
 * not sandbox-confirmed (see #81/#82) — these tests pin down the {@code long}
 * minor-units -&gt; {@code String} formatting contract this fix introduces,
 * not the provider's real API contract.
 */
@ExtendWith(MockitoExtension.class)
class MoneyMinorUnitsTest {

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

    @ParameterizedTest(name = "{0} minor units -> \"{1}\"")
    @CsvSource({
            "1010, 10.10",
            "7, 0.07",
            "12345, 123.45",
            "0, 0.00",
            "1, 0.01",
            "100, 1.00",
            "999999999, 9999999.99"
    })
    void montonio_createOneOff_sendsExactMajorUnitAmount(long amountMinor, String expectedMajor) {
        OneOffPaymentRequest request = OneOffPaymentRequest.builder()
                .orderId("order-1")
                .amountMinor(amountMinor)
                .currency("EUR")
                .companyId("company-1")
                .build();

        when(montonioClient.postOrder(any(), eq("company-1")))
                .thenReturn(Collections.singletonMap("uuid", "montonio-ref-1"));

        montonioProvider.createOneOff(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(montonioClient).postOrder(captor.capture(), eq("company-1"));
        Map<String, Object> order = captor.getValue();

        assertThat(order.get("grandTotal")).isEqualTo(expectedMajor);
        assertThat(order.get("grandTotal")).isInstanceOf(String.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> payment = (Map<String, Object>) order.get("payment");
        assertThat(payment.get("amount")).isEqualTo(expectedMajor);
        assertThat(payment.get("amount")).isInstanceOf(String.class);
    }

    @ParameterizedTest(name = "{0} minor units -> \"{1}\"")
    @CsvSource({
            "1010, 10.10",
            "7, 0.07",
            "12345, 123.45",
            "0, 0.00",
            "1, 0.01",
            "100, 1.00",
            "999999999, 9999999.99"
    })
    void montonio_chargeRecurring_sendsExactMajorUnitAmount(long amountMinor, String expectedMajor) {
        RecurringChargeRequest request = RecurringChargeRequest.builder()
                .orderId("order-1")
                .amountMinor(amountMinor)
                .currency("EUR")
                .tokenRef("token-1")
                .companyId("company-1")
                .build();

        when(montonioClient.postOrder(any(), eq("company-1")))
                .thenReturn(Collections.singletonMap("uuid", "montonio-ref-1"));

        montonioProvider.chargeRecurring(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(montonioClient).postOrder(captor.capture(), eq("company-1"));

        assertThat(captor.getValue().get("grandTotal")).isEqualTo(expectedMajor);
    }

    @ParameterizedTest(name = "{0} minor units -> \"{1}\"")
    @CsvSource({
            "1010, 10.10",
            "7, 0.07",
            "12345, 123.45",
            "1, 0.01",
            "100, 1.00"
    })
    void montonio_refund_sendsExactMajorUnitAmount(long amountMinor, String expectedMajor) {
        RefundRequest request = RefundRequest.builder()
                .providerRef("montonio-ref-1")
                .amountMinor(amountMinor)
                .build();

        lenient().when(paymentRepository.findByProviderRef(anyString())).thenReturn(java.util.Optional.empty());
        when(montonioClient.postRefund(any(), isNull()))
                .thenReturn(Collections.singletonMap("uuid", "refund-ref-1"));

        montonioProvider.refund(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(montonioClient).postRefund(captor.capture(), isNull());

        assertThat(captor.getValue().get("amount")).isEqualTo(expectedMajor);
    }

    @ParameterizedTest(name = "{0} minor units -> \"{1}\"")
    @CsvSource({
            "1010, 10.10",
            "7, 0.07",
            "12345, 123.45",
            "0, 0.00",
            "1, 0.01",
            "100, 1.00",
            "999999999, 9999999.99"
    })
    void everypay_createOneOff_sendsExactMajorUnitAmount(long amountMinor, String expectedMajor) {
        OneOffPaymentRequest request = OneOffPaymentRequest.builder()
                .orderId("order-1")
                .amountMinor(amountMinor)
                .currency("EUR")
                .companyId("company-1")
                .build();

        when(everyPayClient.config()).thenReturn(new com.comforthub.backoffice.payment.config.PaymentProperties.Everypay());
        when(everyPayClient.post(eq("/payments/oneoff"), any(), eq("company-1")))
                .thenReturn(Map.of("payment_reference", "everypay-ref-1", "payment_link", "https://redirect"));

        everyPayProvider.createOneOff(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(everyPayClient).post(eq("/payments/oneoff"), captor.capture(), eq("company-1"));

        assertThat(captor.getValue().get("amount")).isEqualTo(expectedMajor);
        assertThat(captor.getValue().get("amount")).isInstanceOf(String.class);
    }

    @ParameterizedTest(name = "{0} minor units -> \"{1}\"")
    @CsvSource({
            "1010, 10.10",
            "7, 0.07",
            "12345, 123.45",
            "1, 0.01",
            "100, 1.00"
    })
    void everypay_chargeRecurring_sendsExactMajorUnitAmount(long amountMinor, String expectedMajor) {
        RecurringChargeRequest request = RecurringChargeRequest.builder()
                .orderId("order-1")
                .amountMinor(amountMinor)
                .currency("EUR")
                .tokenRef("token-1")
                .companyId("company-1")
                .build();

        when(everyPayClient.config()).thenReturn(new com.comforthub.backoffice.payment.config.PaymentProperties.Everypay());
        when(everyPayClient.post(eq("/payments/mit"), any(), eq("company-1")))
                .thenReturn(Map.of("payment_reference", "everypay-ref-1", "payment_state", "settled"));

        everyPayProvider.chargeRecurring(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(everyPayClient).post(eq("/payments/mit"), captor.capture(), eq("company-1"));

        assertThat(captor.getValue().get("amount")).isEqualTo(expectedMajor);
    }

    @ParameterizedTest(name = "{0} minor units -> \"{1}\"")
    @CsvSource({
            "1010, 10.10",
            "7, 0.07",
            "12345, 123.45",
            "1, 0.01",
            "100, 1.00"
    })
    void everypay_refund_sendsExactMajorUnitAmount(long amountMinor, String expectedMajor) {
        RefundRequest request = RefundRequest.builder()
                .providerRef("everypay-ref-1")
                .amountMinor(amountMinor)
                .build();

        lenient().when(paymentRepository.findByProviderRef(anyString())).thenReturn(java.util.Optional.empty());
        when(everyPayClient.config()).thenReturn(new com.comforthub.backoffice.payment.config.PaymentProperties.Everypay());
        when(everyPayClient.post(eq("/payments/everypay-ref-1/refund"), any(), isNull()))
                .thenReturn(Collections.singletonMap("payment_reference", "refund-ref-1"));

        everyPayProvider.refund(request);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(everyPayClient).post(eq("/payments/everypay-ref-1/refund"), captor.capture(), isNull());

        assertThat(captor.getValue().get("amount")).isEqualTo(expectedMajor);
    }

    @Test
    void bigDecimalFormatting_neverProducesBinaryDoubleRoundingArtifacts() {
        // 0.07 EUR (7 minor units) is a classic case where amountMinor / 100.0
        // as a double can misbehave downstream; BigDecimal.valueOf(minor, 2)
        // keeps the exact decimal representation.
        assertThat(java.math.BigDecimal.valueOf(7L, 2).toPlainString()).isEqualTo("0.07");
        assertThat(java.math.BigDecimal.valueOf(1010L, 2).toPlainString()).isEqualTo("10.10");
        assertThat(java.math.BigDecimal.valueOf(12345L, 2).toPlainString()).isEqualTo("123.45");
    }
}
