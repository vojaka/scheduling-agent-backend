package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerNotificationController.class)
@Import({SecurityConfig.class, ObjectMapper.class})
class ConsumerNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private ConsumerUserService consumerUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Map<String, Object> rawNotification(String id, String title, String createdBy, String visibleFor) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Title", title);
        r.put("Internal Message", "Message " + id);
        r.put("isViewed", false);
        r.put("Created By", createdBy);
        if (visibleFor != null) {
            r.put("Visible for", List.of(visibleFor));
        }
        r.put("Created Date", 1782588208910L);
        return r;
    }

    @Test
    void getNotifications_returnsOwnAndClientVisibleNotifications() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));

        BubbleListResult result = new BubbleListResult();
        result.setResults(List.of(
                rawNotification("notif-1", "Own Notif", "user-1", null),
                rawNotification("notif-2", "General Client Notif", "someone-else", "Client"),
                rawNotification("notif-3", "Foreign Staff Notif", "someone-else", "Manager")
        ));
        when(bubbleClient.list(eq("notifications"), any(), any(), any(), any(), eq(true)))
                .thenReturn(result);

        mockMvc.perform(get("/api/consumer/notifications").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("notif-1"))
                .andExpect(jsonPath("$[1].id").value("notif-2"));
    }

    @Test
    void markAsViewed_syntheticId_returnsOkWithoutBubbleCall() throws Exception {
        mockMvc.perform(post("/api/consumer/notifications/cart-expiry-123/view").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void markAsViewed_bubbleId_updatesBubble() throws Exception {
        mockMvc.perform(post("/api/consumer/notifications/bubble-id-123/view").with(jwt()))
                .andExpect(status().isOk());

        verify(bubbleClient).update(eq("notifications"), eq("bubble-id-123"), any());
    }

    @Test
    void getNotifications_requiresJwt() throws Exception {
        mockMvc.perform(get("/api/consumer/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
