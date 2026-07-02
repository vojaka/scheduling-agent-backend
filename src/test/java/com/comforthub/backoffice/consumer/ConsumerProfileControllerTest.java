package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.consumer.mapper.ConsumerProfileBubbleMapper;
import com.comforthub.backoffice.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerProfileController.class)
@Import({SecurityConfig.class, ConsumerProfileBubbleMapper.class})
class ConsumerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private ConsumerUserService consumerUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Map<String, Object> rawUser(String id) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("firstName", "Kim");
        r.put("lastName", "Smirnov");
        r.put("FullName", "Kim Smirnov");
        r.put("phonePrefix", "+372");
        r.put("phoneNumber", "5551234");
        r.put("language", "en");
        r.put("Verified Profile", true);
        r.put("Roles", List.of("Client"));
        r.put("Cart (single)", "cart-1");
        return r;
    }

    @Test
    void getProfile_returnsOwnBubbleUser() throws Exception {
        when(consumerUserService.currentBubbleUser()).thenReturn(Optional.of(rawUser("user-1")));

        mockMvc.perform(get("/api/consumer/profile").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.firstName").value("Kim"))
                .andExpect(jsonPath("$.fullName").value("Kim Smirnov"))
                .andExpect(jsonPath("$.verifiedProfile").value(true))
                .andExpect(jsonPath("$.roles[0]").value("Client"))
                .andExpect(jsonPath("$.cartId").value("cart-1"));
    }

    @Test
    void updateProfile_patchesOwnRecordOnly() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        when(bubbleClient.get("user", "user-1")).thenReturn(rawUser("user-1"));

        mockMvc.perform(patch("/api/consumer/profile").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Kim\",\"language\":\"et\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"));

        verify(bubbleClient).update("user", "user-1",
                Map.of("firstName", "Kim", "language", "et"));
    }

    @Test
    void verifyPhone_runsBubbleTwilioWorkflow() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        when(bubbleClient.runWorkflow(anyString(), any())).thenReturn(Map.of("status", "pending"));

        mockMvc.perform(post("/api/consumer/profile/verify-phone").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+3725551234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));

        verify(bubbleClient).runWorkflow(eq("verification"),
                eq(Map.of("phone_number", "+3725551234")));
    }

    @Test
    void confirmPhone_missingCode_returnsBadRequest() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));

        mockMvc.perform(post("/api/consumer/profile/confirm-phone").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+3725551234\"}"))
                .andExpect(status().isBadRequest());

        verify(bubbleClient, never()).runWorkflow(anyString(), any());
    }

    @Test
    void profile_requiresJwt() throws Exception {
        mockMvc.perform(get("/api/consumer/profile"))
                .andExpect(status().isUnauthorized());
    }
}
