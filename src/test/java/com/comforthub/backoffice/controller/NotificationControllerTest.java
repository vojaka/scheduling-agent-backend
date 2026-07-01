package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.dto.NotificationDto;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.SmartNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for {@link NotificationController} — company scoping of the
 * notification feed and the synthetic-vs-Bubble split on mark-as-viewed
 * (locally computed reminders are never written back to Bubble).
 */
@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    private static final String COMPANY = "company-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SmartNotificationService notificationService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void getNotifications_returnsCompanyScopedFeed() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(notificationService.getNotificationsForCompany(COMPANY)).thenReturn(List.of(
                NotificationDto.builder().id("n-1").title("Stock low").company(COMPANY).build()));

        mockMvc.perform(get("/api/notifications").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("n-1"))
                .andExpect(jsonPath("$[0].title").value("Stock low"))
                .andExpect(jsonPath("$[0].company").value(COMPANY));
    }

    @Test
    void getNotifications_noResolvableCompany_returnsEmptyList_withoutServiceCall() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/notifications").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(notificationService, never()).getNotificationsForCompany(any());
    }

    @Test
    void markAsViewed_syntheticReminderId_isLocalOnly() throws Exception {
        mockMvc.perform(post("/api/notifications/stock-low-abc123/view").with(jwt()))
                .andExpect(status().isOk());

        // Synthetic reminders are computed locally — never written back to Bubble.
        verify(notificationService, never()).markAsViewed(any());
    }

    @Test
    void markAsViewed_bubbleNotificationId_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/notifications/1700000000000x123/view").with(jwt()))
                .andExpect(status().isOk());

        verify(notificationService).markAsViewed("1700000000000x123");
    }
}
