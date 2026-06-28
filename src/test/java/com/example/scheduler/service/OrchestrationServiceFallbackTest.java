package com.example.scheduler.service;

import com.example.scheduler.client.BubbleClient;
import com.example.scheduler.exception.GeminiUnavailableException;
import com.example.scheduler.model.BubbleUser;
import com.example.scheduler.model.BubbleStore;
import com.example.scheduler.model.BubbleAvailability;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class OrchestrationServiceFallbackTest {

    private OrchestrationService orchestrationService;
    private RestTemplate mockRestTemplate;
    private BubbleClient mockBubbleClient;

    @BeforeEach
    public void setup() {
        mockBubbleClient = Mockito.mock(BubbleClient.class);
        mockRestTemplate = Mockito.mock(RestTemplate.class);
        
        DeterministicValidator validator = new DeterministicValidator();
        ObjectMapper objectMapper = new ObjectMapper();
        
        orchestrationService = new OrchestrationService(mockBubbleClient, validator, objectMapper);
        
        // Inject fields via reflection
        ReflectionTestUtils.setField(orchestrationService, "geminiApiKey", "fake-api-key");
        ReflectionTestUtils.setField(orchestrationService, "geminiModel", "gemini-2.5-flash");
        ReflectionTestUtils.setField(orchestrationService, "geminiFallbackModel", "gemini-1.5-flash");
        ReflectionTestUtils.setField(orchestrationService, "restTemplate", mockRestTemplate);
    }

    @Test
    public void testGenerateScheduleBothModelsFailThrowsGeminiUnavailableException() {
        // Stub BubbleClient calls
        Mockito.when(mockBubbleClient.fetchUsers()).thenReturn(Collections.singletonList(
                new BubbleUser("1731963242067x219606905011096030", "Kim Smirnov", "Worker", 40, true)
        ));
        Mockito.when(mockBubbleClient.fetchWageRates()).thenReturn(Collections.emptyList());
        Mockito.when(mockBubbleClient.fetchStores()).thenReturn(Collections.emptyList());
        Mockito.when(mockBubbleClient.fetchAvailability()).thenReturn(Collections.emptyList());

        HttpServerErrorException.ServiceUnavailable serviceUnavailableException = 
                (HttpServerErrorException.ServiceUnavailable) HttpServerErrorException.create(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "503 Service Unavailable", 
                        new org.springframework.http.HttpHeaders(), 
                        new byte[0], 
                        StandardCharsets.UTF_8
                );
        
        Mockito.when(mockRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(serviceUnavailableException);

        // Verify that calling generateSchedule throws GeminiUnavailableException
        assertThrows(GeminiUnavailableException.class, () -> {
            orchestrationService.generateSchedule("Schedule shifts", "2026-07", "company-123", Collections.emptyList(), 0, 0);
        });

        // Verify it attempted calling both models (primary gemini-2.5-flash and fallback gemini-1.5-flash)
        // Since maxRetries = 3, primary takes 3 attempts, fallback takes 3 attempts = 6 total calls
        Mockito.verify(mockRestTemplate, Mockito.times(6))
                .postForObject(any(String.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    public void testGenerateSchedulePrimaryFailsFallbackSucceeds() throws Exception {
        // Stub BubbleClient calls
        Mockito.when(mockBubbleClient.fetchUsers()).thenReturn(Collections.singletonList(
                new BubbleUser("1731963242067x219606905011096030", "Kim Smirnov", "Worker", 40, true)
        ));
        Mockito.when(mockBubbleClient.fetchWageRates()).thenReturn(Collections.emptyList());
        Mockito.when(mockBubbleClient.fetchStores()).thenReturn(Collections.emptyList());
        Mockito.when(mockBubbleClient.fetchAvailability()).thenReturn(Collections.emptyList());

        HttpServerErrorException.ServiceUnavailable serviceUnavailableException = 
                (HttpServerErrorException.ServiceUnavailable) HttpServerErrorException.create(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "503 Service Unavailable", 
                        new org.springframework.http.HttpHeaders(), 
                        new byte[0], 
                        StandardCharsets.UTF_8
                );

        // Mock response for fallback model
        String mockResponseJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"proposedShifts\\\":[]}\"}]}}]}";

        Mockito.when(mockRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(serviceUnavailableException) // attempt 1 primary
                .thenThrow(serviceUnavailableException) // attempt 2 primary
                .thenThrow(serviceUnavailableException) // attempt 3 primary
                .thenReturn(mockResponseJson);          // fallback succeeds

        var response = orchestrationService.generateSchedule("Schedule shifts", "2026-07", "company-123", Collections.emptyList(), 0, 0);

        // Assert response logs show primary failure and fallback warning
        assertTrue(response.getOrchestratorLogs().stream().anyMatch(log -> log.contains("WARNING: Primary model gemini-2.5-flash failed")));
        assertTrue(response.getOrchestratorLogs().stream().anyMatch(log -> log.contains("Calling Gemini API (gemini-1.5-flash)")));
    }

    @Test
    public void testPrimary429ImmediatelyFallsBackWithoutRetrying() throws Exception {
        // Stub BubbleClient calls
        Mockito.when(mockBubbleClient.fetchUsers()).thenReturn(Collections.singletonList(
                new BubbleUser("1731963242067x219606905011096030", "Kim Smirnov", "Worker", 40, true)
        ));
        Mockito.when(mockBubbleClient.fetchWageRates()).thenReturn(Collections.emptyList());
        Mockito.when(mockBubbleClient.fetchStores()).thenReturn(Collections.emptyList());
        Mockito.when(mockBubbleClient.fetchAvailability()).thenReturn(Collections.emptyList());

        // Simulate 429 on primary model
        HttpClientErrorException.TooManyRequests tooManyRequestsException =
                (HttpClientErrorException.TooManyRequests) HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "429 Too Many Requests",
                        new org.springframework.http.HttpHeaders(),
                        new byte[0],
                        StandardCharsets.UTF_8
                );

        // Mock response for fallback model
        String mockResponseJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"proposedShifts\\\":[]}\"}]}}]}";

        Mockito.when(mockRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(tooManyRequestsException) // primary: 429 — should NOT retry
                .thenReturn(mockResponseJson);        // fallback succeeds on first attempt

        var response = orchestrationService.generateSchedule("Schedule shifts", "2026-07", "company-123", Collections.emptyList(), 0, 0);

        // Assert log shows immediate 429 warning and fallback invocation
        assertTrue(response.getOrchestratorLogs().stream().anyMatch(log -> log.contains("429 Too Many Requests")));
        assertTrue(response.getOrchestratorLogs().stream().anyMatch(log -> log.contains("Calling Gemini API (gemini-1.5-flash)")));

        // Primary was called exactly once (429 → no retries), fallback called exactly once = 2 total
        Mockito.verify(mockRestTemplate, Mockito.times(2))
                .postForObject(any(String.class), any(HttpEntity.class), eq(String.class));
    }
}
