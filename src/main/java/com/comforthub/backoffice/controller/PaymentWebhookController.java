package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.payment.PaymentException;
import com.comforthub.backoffice.payment.PaymentService;
import com.comforthub.backoffice.payment.ProviderKey;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Public payment webhook receivers. These sit OUTSIDE {@code /api/**} so they
 * are not subject to Auth0 JWT auth (SecurityConfig permits everything except
 * {@code /api/**}); each request is instead verified by signature in the
 * provider implementation.
 */
@RestController
@RequestMapping("/webhooks")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/montonio")
    public ResponseEntity<Void> montonio(@RequestBody(required = false) String body,
                                         @RequestHeader Map<String, String> headers) {
        return handle(ProviderKey.MONTONIO, body, headers);
    }

    @PostMapping("/everypay")
    public ResponseEntity<Void> everypay(@RequestBody(required = false) String body,
                                         @RequestHeader Map<String, String> headers) {
        return handle(ProviderKey.EVERYPAY, body, headers);
    }

    private ResponseEntity<Void> handle(ProviderKey key, String body, Map<String, String> headers) {
        try {
            paymentService.handleWebhook(key, body == null ? "" : body, lower(headers));
            return ResponseEntity.ok().build();
        } catch (PaymentException e) {
            // Permanent failure (e.g. bad signature) — 400 so the provider stops retrying.
            return ResponseEntity.badRequest().build();
        }
    }

    private static Map<String, String> lower(Map<String, String> headers) {
        Map<String, String> copy = new HashMap<>();
        headers.forEach((k, v) -> copy.put(k.toLowerCase(), v));
        return copy;
    }
}
