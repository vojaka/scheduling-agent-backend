package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.payment.PaymentStatus;
import com.comforthub.backoffice.payment.PaymentService;
import com.comforthub.backoffice.payment.config.PaymentProperties;
import com.comforthub.backoffice.payment.dto.OneOffPaymentRequest;
import com.comforthub.backoffice.payment.dto.PaymentSession;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security + company-scoping contract for {@code /api/payments/**}:
 *
 * <ul>
 *   <li>No token → <b>401</b> (the {@code /api/**} chain is active).</li>
 *   <li>Authenticated but no company resolves from the principal → <b>403</b>.</li>
 *   <li>Authenticated with a company → <b>200</b>, and {@code companyId} is taken
 *       from the principal, overriding whatever the request body claims.</li>
 * </ul>
 *
 * The company-resolution logic itself is covered by {@code CurrentUserServiceTest};
 * here it is mocked so the test asserts only the controller wiring + scoping.
 */
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private PaymentProperties paymentProperties;

    @MockBean
    private CurrentUserService currentUserService;

    // Replaces the real decoder so SecurityConfig never calls Auth0 at startup.
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void unauthenticated_oneOff_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/payments/one-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":1000,\"currency\":\"EUR\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_withoutCompany_isForbidden() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/payments/one-off")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":1000,\"currency\":\"EUR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticated_scopesCompanyFromPrincipal_notFromBody() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-from-token"));
        when(paymentService.payOneOff(any())).thenReturn(
                PaymentSession.builder().status(PaymentStatus.PENDING).build());

        mockMvc.perform(post("/api/payments/one-off")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        // The body tries to spoof another company; it must be ignored.
                        .content("{\"companyId\":\"attacker-company\",\"amountMinor\":1000,\"currency\":\"EUR\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<OneOffPaymentRequest> captor = ArgumentCaptor.forClass(OneOffPaymentRequest.class);
        verify(paymentService).payOneOff(captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo("company-from-token");
    }
}
