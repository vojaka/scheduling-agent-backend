package com.example.scheduler.controller;

import com.example.scheduler.client.BubbleClient;
import com.example.scheduler.dto.ScheduleGenerateResponse;
import com.example.scheduler.dto.ValidationReport;
import com.example.scheduler.security.ApiKeyFilter;
import com.example.scheduler.service.OrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleController.class)
@Import(ApiKeyFilter.class)
@TestPropertySource(properties = {
    "app.api-key=test-api-key-123",
    "bubble.api.token=test-bubble-token"
})
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrchestrationService orchestrationService;

    @MockBean
    private BubbleClient bubbleClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGenerateWithoutApiKeyShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/schedule/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"Schedule standard week\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized: Missing or invalid X-API-KEY header"));
    }

    @Test
    void testGenerateWithInvalidApiKeyShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/schedule/generate")
                        .header("X-API-KEY", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"Schedule standard week\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized: Missing or invalid X-API-KEY header"));
    }

    @Test
    void testGenerateWithValidApiKeyShouldReturn200() throws Exception {
        ScheduleGenerateResponse mockResponse = new ScheduleGenerateResponse(
                Collections.emptyList(),
                new ValidationReport(true, Collections.emptyList()),
                Collections.singletonList("Testing trace log")
        );

        Mockito.when(orchestrationService.generateSchedule(any(), any(), any(), any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/schedule/generate")
                        .header("X-API-KEY", "test-api-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"Schedule standard week\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationReport.valid").value(true))
                .andExpect(jsonPath("$.orchestratorLogs[0]").value("Testing trace log"));
    }
}
