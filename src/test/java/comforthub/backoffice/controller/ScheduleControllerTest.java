package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.dto.ScheduleGenerateResponse;
import com.comforthub.backoffice.dto.ValidationReport;
import com.comforthub.backoffice.exception.GeminiUnavailableException;
import com.comforthub.backoffice.service.BubbleSyncService;
import com.comforthub.backoffice.service.ScheduleOrchestrationService;
import com.comforthub.backoffice.service.ShiftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice test. Security (JWT) is exercised separately by SecurityConfig;
 * here filters are disabled so the focus is on controller logic and error mapping.
 */
@WebMvcTest(controllers = ScheduleController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "bubble.api.token=test-bubble-token"
})
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleOrchestrationService orchestrationService;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private BubbleSyncService bubbleSyncService;

    @MockBean
    private ShiftService shiftService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGenerateReturns200() throws Exception {
        ScheduleGenerateResponse mockResponse = new ScheduleGenerateResponse(
                Collections.emptyList(),
                new ValidationReport(true, Collections.emptyList()),
                Collections.singletonList("Testing trace log")
        );

        Mockito.when(orchestrationService.generateSchedule(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/schedule/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"Schedule standard week\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationReport.valid").value(true))
                .andExpect(jsonPath("$.orchestratorLogs[0]").value("Testing trace log"));
    }

    @Test
    void testGenerateWithGeminiUnavailableShouldReturn503() throws Exception {
        Mockito.when(orchestrationService.generateSchedule(any()))
                .thenThrow(new GeminiUnavailableException("Gemini API is currently unavailable: 503 Service Unavailable"));

        mockMvc.perform(post("/api/schedule/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"Schedule standard week\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Gemini API is currently unavailable: 503 Service Unavailable"));
    }
}
