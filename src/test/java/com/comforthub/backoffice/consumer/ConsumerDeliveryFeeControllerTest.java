package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerDeliveryFeeController.class)
@Import({SecurityConfig.class, ConsumerConfig.class})
class ConsumerDeliveryFeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void deliveryFee_defaultPolicy_isBasePlusPerKm() throws Exception {
        // Default policy mirrors Bubble's formula: €5 base + €1/km.
        mockMvc.perform(get("/api/consumer/delivery-fee").param("distanceKm", "3.2").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceKm").value(3.2))
                .andExpect(jsonPath("$.fee").value(8.2))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void deliveryFee_zeroDistance_isBaseFee() throws Exception {
        mockMvc.perform(get("/api/consumer/delivery-fee").param("distanceKm", "0").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(5.0));
    }

    @Test
    void deliveryFee_negativeDistance_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/consumer/delivery-fee").param("distanceKm", "-1").with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deliveryFee_requiresJwt() throws Exception {
        mockMvc.perform(get("/api/consumer/delivery-fee").param("distanceKm", "1"))
                .andExpect(status().isUnauthorized());
    }
}
