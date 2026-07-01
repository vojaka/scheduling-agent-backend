package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.exception.ForbiddenException;
import com.comforthub.backoffice.payment.PaymentService;
import com.comforthub.backoffice.payment.ProviderKey;
import com.comforthub.backoffice.payment.config.PaymentProperties;
import com.comforthub.backoffice.payment.dto.*;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payment endpoints. Behind {@code /api/**} — requires an Auth0 JWT.
 *
 * <p>Company scope is derived from the authenticated JWT principal (via
 * {@link CurrentUserService}) and stamped onto each request — it is never
 * trusted from the request body. This mirrors the company scoping used by the
 * other {@code /api/**} controllers (e.g. {@code OrderController}).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProperties properties;
    private final CurrentUserService currentUserService;

    public PaymentController(PaymentService paymentService, PaymentProperties properties,
                             CurrentUserService currentUserService) {
        this.paymentService = paymentService;
        this.properties = properties;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/one-off")
    public ResponseEntity<PaymentSession> oneOff(@RequestBody OneOffPaymentRequest request) {
        request.setCompanyId(requireCompany());
        return ResponseEntity.ok(paymentService.payOneOff(request));
    }

    @PostMapping("/recurring/init")
    public ResponseEntity<PaymentSession> recurringInit(@RequestBody RecurringInitRequest request) {
        request.setCompanyId(requireCompany());
        return ResponseEntity.ok(paymentService.startRecurring(request));
    }

    @PostMapping("/recurring/charge")
    public ResponseEntity<PaymentResult> recurringCharge(@RequestBody RecurringChargeRequest request) {
        request.setCompanyId(requireCompany());
        return ResponseEntity.ok(paymentService.chargeRecurring(request));
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundResult> refund(@RequestBody RefundRequest request) {
        // Refunds are keyed by the provider payment reference; RefundRequest carries
        // no companyId. Require the caller to belong to a company (defence in depth) —
        // ownership of the referenced payment is enforced once persistence lands (#83).
        requireCompany();
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

    /**
     * The caller's company id, derived from the authenticated JWT principal —
     * never trusted from the request body. Callers with no resolvable company
     * are rejected with 403, matching the scoping used by the other controllers.
     */
    private String requireCompany() {
        return currentUserService.currentCompanyId()
                .orElseThrow(() -> new ForbiddenException(
                        "No company is associated with the authenticated user."));
    }
}
