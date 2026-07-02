package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerGuestController.class)
@Import(SecurityConfig.class)
class ConsumerGuestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void createGuest_createsShadowBubbleUser_fromEmailOnly() throws Exception {
        when(bubbleClient.create(eq("user"), any())).thenReturn("guest-user-1");

        mockMvc.perform(post("/api/consumer/guest").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guest@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("guest-user-1"));

        verify(bubbleClient).create("user", Map.of("email", "guest@example.com"));
    }

    @Test
    void createGuest_invalidEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/consumer/guest").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verify(bubbleClient, never()).create(anyString(), any());
    }

    @Test
    void createGuest_requiresJwt() throws Exception {
        mockMvc.perform(post("/api/consumer/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guest@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }
}
