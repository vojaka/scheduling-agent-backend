package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.payment.PaymentException;
import com.comforthub.backoffice.payment.PaymentService;
import com.comforthub.backoffice.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The webhook receivers sit OUTSIDE {@code /api/**} and must be reachable
 * WITHOUT an Auth0 token — they are authenticated by provider signature inside
 * the provider implementation instead. A permanent failure (e.g. bad signature)
 * surfaces as a {@link PaymentException} → <b>400</b> so the provider stops
 * retrying.
 */
@WebMvcTest(PaymentWebhookController.class)
@Import(SecurityConfig.class)
class PaymentWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    // Replaces the real decoder so SecurityConfig never calls Auth0 at startup.
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void montonioWebhook_isPublic_returnsOkWithoutToken() throws Exception {
        // No .with(jwt()) — the endpoint must not be behind Auth0.
        mockMvc.perform(post("/webhooks/montonio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void badSignature_returnsBadRequest() throws Exception {
        doThrow(new PaymentException("Invalid webhook signature for EVERYPAY"))
                .when(paymentService).handleWebhook(any(), any(), any());

        mockMvc.perform(post("/webhooks/everypay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
