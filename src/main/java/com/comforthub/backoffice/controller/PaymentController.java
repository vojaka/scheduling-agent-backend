package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.payment.PaymentService;
import com.comforthub.backoffice.payment.ProviderKey;
import com.comforthub.backoffice.payment.config.PaymentProperties;
import com.comforthub.backoffice.payment.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payment endpoints. Behind {@code /api/**} — requires an Auth0 JWT.
 * Company scoping from the JWT principal is wired with the domain-entity work.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProperties properties;

    public PaymentController(PaymentService paymentService, PaymentProperties properties) {
        this.paymentService = paymentService;
        this.properties = properties;
    }

    @PostMapping("/one-off")
    public ResponseEntity<PaymentSession> oneOff(@RequestBody OneOffPaymentRequest request) {
        return ResponseEntity.ok(paymentService.payOneOff(request));
    }

    @PostMapping("/recurring/init")
    public ResponseEntity<PaymentSession> recurringInit(@RequestBody RecurringInitRequest request) {
        return ResponseEntity.ok(paymentService.startRecurring(request));
    }

    @PostMapping("/recurring/charge")
    public ResponseEntity<PaymentResult> recurringCharge(@RequestBody RecurringChargeRequest request) {
        return ResponseEntity.ok(paymentService.chargeRecurring(request));
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundResult> refund(@RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(request));
    }

    /** Enabled payment methods per provider — the frontend renders these. */
    @GetMapping("/methods")
    public ResponseEntity<Map<String, Object>> methods() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defaultProvider", properties.getDefaultProvider().name());
        Map<String, Object> byProvider = new LinkedHashMap<>();
        byProvider.put(ProviderKey.MONTONIO.name(), properties.getMontonio().getEnabledMethods());
        byProvider.put(ProviderKey.EVERYPAY.name(), properties.getEverypay().getEnabledMethods());
        out.put("providers", byProvider);
        return ResponseEntity.ok(out);
    }
}
