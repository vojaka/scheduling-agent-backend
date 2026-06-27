package com.example.scheduler.client;

import com.example.scheduler.model.BubbleShift;
import com.example.scheduler.model.BubbleUser;
import com.example.scheduler.model.BubbleWageRate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class BubbleClient {

    private static final Logger log = LoggerFactory.getLogger(BubbleClient.class);

    private final RestClient restClient;

    @Value("${bubble.api.token}")
    private String bubbleToken;

    public BubbleClient(RestClient.Builder restClientBuilder, 
                        @Value("${bubble.api.base-url}") String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    private RestClient.RequestHeadersSpec<?> addAuthHeader(RestClient.RequestHeadersSpec<?> requestSpec) {
        if (bubbleToken != null && !bubbleToken.isEmpty() && !bubbleToken.equals("default-bubble-token")) {
            return requestSpec.header("Authorization", "Bearer " + bubbleToken);
        }
        return requestSpec;
    }

    public List<BubbleUser> fetchUsers() {
        try {
            log.info("Fetching users from Bubble Data API...");
            BubbleResponseWrapper<BubbleUser> response = addAuthHeader(
                    restClient.get()
                            .uri("/user")
                            .accept(MediaType.APPLICATION_JSON)
            )
            .retrieve()
            .body(new ParameterizedTypeReference<BubbleResponseWrapper<BubbleUser>>() {});

            if (response != null && response.getResponse() != null && response.getResponse().getResults() != null) {
                return response.getResponse().getResults();
            }
        } catch (Exception e) {
            log.error("Failed to fetch users from Bubble: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<BubbleWageRate> fetchWageRates() {
        try {
            log.info("Fetching wage rates from Bubble Data API...");
            BubbleResponseWrapper<BubbleWageRate> response = addAuthHeader(
                    restClient.get()
                            .uri("/wagerate")
                            .accept(MediaType.APPLICATION_JSON)
            )
            .retrieve()
            .body(new ParameterizedTypeReference<BubbleResponseWrapper<BubbleWageRate>>() {});

            if (response != null && response.getResponse() != null && response.getResponse().getResults() != null) {
                return response.getResponse().getResults();
            }
        } catch (Exception e) {
            log.error("Failed to fetch wage rates from Bubble: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public BubbleShift createShift(BubbleShift shift) {
        try {
            log.info("Creating shift in Bubble for user: {} ({} - {})", 
                    shift.getAssignedUser(), shift.getStartTime(), shift.getEndTime());
            
            // Note: Bubble Data API returns the created object inside a wrapper, or directly depending on version.
            // Typically, POST /obj/shift returns a JSON with { "status": "success", "id": "created-id" }
            // Let's perform a simple POST and return the result.
            BubbleCreationResponse response = addAuthHeader(
                    restClient.post()
                            .uri("/shift")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(shift)
            )
            .retrieve()
            .body(BubbleCreationResponse.class);

            if (response != null) {
                shift.setId(response.getId());
            }
            return shift;
        } catch (Exception e) {
            log.error("Failed to create shift in Bubble: {}", e.getMessage());
            throw new RuntimeException("Bubble create failed: " + e.getMessage(), e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BubbleResponseWrapper<T> {
        private ResponseData<T> response;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ResponseData<T> {
            private List<T> results = new ArrayList<>();
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BubbleCreationResponse {
        private String status;
        private String id;
    }
}
