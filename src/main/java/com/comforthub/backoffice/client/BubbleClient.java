package com.comforthub.backoffice.client;

import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.model.BubbleStore;
import com.comforthub.backoffice.model.BubbleUser;
import com.comforthub.backoffice.model.BubbleWageRate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BubbleClient {

    private static final Logger log = LoggerFactory.getLogger(BubbleClient.class);

    private final RestClient restClient;
    private final String bubbleBaseUrl;

    @Value("${bubble.api.token}")
    private String bubbleToken;

    public BubbleClient(RestClient.Builder restClientBuilder,
                        @Value("${bubble.api.base-url}") String baseUrl) {
        this.bubbleBaseUrl = baseUrl;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                // JDK HttpClient supports PATCH (used by update()); the default
                // SimpleClientHttpRequestFactory does not.
                .requestFactory(new JdkClientHttpRequestFactory())
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

    public List<BubbleStore> fetchStores() {
        try {
            log.info("Fetching stores from Bubble Data API...");
            BubbleResponseWrapper<BubbleStore> response = addAuthHeader(
                    restClient.get()
                            .uri("/store")
                            .accept(MediaType.APPLICATION_JSON)
            )
            .retrieve()
            .body(new ParameterizedTypeReference<BubbleResponseWrapper<BubbleStore>>() {});

            if (response != null && response.getResponse() != null && response.getResponse().getResults() != null) {
                return response.getResponse().getResults();
            }
        } catch (Exception e) {
            log.error("Failed to fetch stores from Bubble: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<BubbleAvailability> fetchAvailability() {
        try {
            log.info("Fetching availability records from Bubble Data API...");
            BubbleResponseWrapper<BubbleAvailability> response = addAuthHeader(
                    restClient.get()
                            .uri("/availability")
                            .accept(MediaType.APPLICATION_JSON)
            )
            .retrieve()
            .body(new ParameterizedTypeReference<BubbleResponseWrapper<BubbleAvailability>>() {});

            if (response != null && response.getResponse() != null && response.getResponse().getResults() != null) {
                return response.getResponse().getResults();
            }
        } catch (Exception e) {
            log.error("Failed to fetch availability from Bubble: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<BubbleShift> fetchShifts() {
        try {
            log.info("Fetching shifts from Bubble Data API...");
            BubbleResponseWrapper<BubbleShift> response = addAuthHeader(
                    restClient.get()
                            .uri("/shift")
                            .accept(MediaType.APPLICATION_JSON)
            )
            .retrieve()
            .body(new ParameterizedTypeReference<BubbleResponseWrapper<BubbleShift>>() {});

            if (response != null && response.getResponse() != null && response.getResponse().getResults() != null) {
                return response.getResponse().getResults();
            }
        } catch (Exception e) {
            log.error("Failed to fetch shifts from Bubble: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchCompanies() {
        try {
            log.info("Fetching companies from Bubble Data API...");
            BubbleResponseWrapper<Map<String, Object>> response = addAuthHeader(
                    restClient.get()
                            .uri("/company")
                            .accept(MediaType.APPLICATION_JSON)
            )
            .retrieve()
            .body(new ParameterizedTypeReference<BubbleResponseWrapper<Map<String, Object>>>() {});

            if (response != null && response.getResponse() != null && response.getResponse().getResults() != null) {
                return response.getResponse().getResults();
            }
        } catch (Exception e) {
            log.error("Failed to fetch companies from Bubble: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public BubbleShift createShift(BubbleShift shift) {
        try {
            log.info("Creating shift in Bubble for user: {} ({} - {})",
                    shift.getAssignedUser(), shift.getStartTime(), shift.getEndTime());

            BubbleShiftPostPayload payload = new BubbleShiftPostPayload();
            payload.setAssignedCompany(shift.getAssignedCompany());
            payload.setAssignedUser(shift.getAssignedUser());
            payload.setEndTime(shift.getEndTime());
            payload.setNotes(shift.getNotes());
            payload.setStartTime(shift.getStartTime());
            payload.setType(shift.getType());
            payload.setStatus(shift.getStatus());
            payload.setAssignedStore(shift.getAssignedStore());

            BubbleCreationResponse response = addAuthHeader(
                    restClient.post()
                            .uri("/shift")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(payload)
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

    // ------------------------------------------------------------------
    // Generic Bubble Data API CRUD primitives.
    //
    // Bubble is the source of truth for the backoffice; PostgreSQL is
    // analytics-only (fed by the hourly ETL). These type-agnostic helpers
    // let controllers proxy the Bubble Data API for any object type. They
    // follow the same addAuthHeader + response-wrapper pattern as the
    // fetch*/createShift helpers above, working with raw JSON maps so the
    // mapping of ugly Bubble field aliases stays in the calling controller.
    // ------------------------------------------------------------------

    /**
     * List records of {@code type}, optionally filtered by a Bubble
     * {@code constraints} JSON array and sorted by {@code sortField}.
     * Maps onto Bubble's cursor pagination: page forward by passing the
     * previous response's {@code cursor + count} as the next {@code cursor}.
     *
     * @param constraintsJson a Bubble constraints JSON array, e.g.
     *        {@code [{"key":"...","constraint_type":"equals","value":"..."}]},
     *        or {@code null}/blank for no filter.
     * @param sortField       Bubble field to sort by, or {@code null} for
     *        Bubble's default ordering.
     * @return results plus Bubble's cursor/count/remaining counters.
     */
    public BubbleListResult list(String type, String constraintsJson, Integer cursor, Integer limit,
                                 String sortField, boolean descending) {
        int c = cursor == null ? 0 : cursor;
        int l = limit == null ? 100 : limit;
        String constraints = (constraintsJson == null || constraintsJson.isBlank()) ? "[]" : constraintsJson;
        try {
            BubbleListResponseWrapper response = addAuthHeader(
                    restClient.get()
                            .uri(uriBuilder -> {
                                uriBuilder.path("/{type}")
                                        .queryParam("constraints", "{constraints}")
                                        .queryParam("cursor", "{cursor}")
                                        .queryParam("limit", "{limit}");
                                if (sortField != null && !sortField.isBlank()) {
                                    uriBuilder.queryParam("sort_field", "{sortField}")
                                            .queryParam("descending", "{descending}");
                                    return uriBuilder.build(type, constraints, c, l, sortField, descending);
                                }
                                return uriBuilder.build(type, constraints, c, l);
                            })
                            .accept(MediaType.APPLICATION_JSON))
                    .retrieve()
                    .body(BubbleListResponseWrapper.class);
            if (response != null && response.getResponse() != null) {
                return response.getResponse();
            }
            return new BubbleListResult();
        } catch (Exception e) {
            log.error("Failed to list {} from Bubble: {}", type, e.getMessage());
            throw new RuntimeException("Bubble list failed for type " + type + ": " + e.getMessage(), e);
        }
    }

    /** Convenience overload with no explicit sort (Bubble default order). */
    public BubbleListResult list(String type, String constraintsJson, Integer cursor, Integer limit) {
        return list(type, constraintsJson, cursor, limit, null, false);
    }

    /**
     * Fetch a single record by Bubble id. Returns {@code null} when Bubble
     * responds 404, so callers can map a missing record to their own
     * not-found response.
     */
    public Map<String, Object> get(String type, String id) {
        try {
            BubbleObjectResponseWrapper response = addAuthHeader(
                    restClient.get()
                            .uri("/{type}/{id}", type, id)
                            .accept(MediaType.APPLICATION_JSON))
                    .retrieve()
                    .body(BubbleObjectResponseWrapper.class);
            return response != null ? response.getResponse() : null;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.error("Failed to get {}/{} from Bubble: {}", type, id, e.getMessage());
            throw new RuntimeException("Bubble get failed for " + type + "/" + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Create a record. Bubble returns the new object's id, which is
     * returned here.
     */
    public String create(String type, Object body) {
        try {
            BubbleCreationResponse response = addAuthHeader(
                    restClient.post()
                            .uri("/{type}", type)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body))
                    .retrieve()
                    .body(BubbleCreationResponse.class);
            return response != null ? response.getId() : null;
        } catch (Exception e) {
            log.error("Failed to create {} in Bubble: {}", type, e.getMessage());
            throw new RuntimeException("Bubble create failed for type " + type + ": " + e.getMessage(), e);
        }
    }

    /**
     * Partially update a record (Bubble PATCH — only the supplied fields are
     * changed). Bubble responds 204 No Content.
     */
    public void update(String type, String id, Object body) {
        try {
            addAuthHeader(
                    restClient.patch()
                            .uri("/{type}/{id}", type, id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to update {}/{} in Bubble: {}", type, id, e.getMessage());
            throw new RuntimeException("Bubble update failed for " + type + "/" + id + ": " + e.getMessage(), e);
        }
    }

    /** Delete a record. Bubble responds 204 No Content. */
    public void delete(String type, String id) {
        try {
            addAuthHeader(
                    restClient.delete()
                            .uri("/{type}/{id}", type, id))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to delete {}/{} in Bubble: {}", type, id, e.getMessage());
            throw new RuntimeException("Bubble delete failed for " + type + "/" + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Lightweight connectivity/auth check used by
     * {@link com.comforthub.backoffice.health.BubbleHealthIndicator} (issue
     * #3). Performs a minimal {@code GET /user?limit=1} — reusing this
     * class's existing RestClient + {@link #addAuthHeader} setup rather than
     * opening a second HTTP client just for health polling — so a single
     * call exercises both network reachability and Bubble token validity.
     *
     * <p>Unlike the other methods here, this deliberately does <em>not</em>
     * catch/swallow exceptions: the caller (the health indicator) needs to
     * see failures in order to report {@code DOWN}.
     */
    public void ping() {
        addAuthHeader(
                restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/user")
                                .queryParam("limit", "1")
                                .build())
                        .accept(MediaType.APPLICATION_JSON))
                .retrieve()
                .toBodilessEntity();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BubbleShiftPostPayload {
        @JsonProperty("assigned_company_custom____merchant")
        private String assignedCompany;

        @JsonProperty("assigned_user_user")
        private String assignedUser;

        @JsonProperty("end_time_date")
        private String endTime;

        @JsonProperty("notes_text")
        private String notes;

        @JsonProperty("start_time_date")
        private String startTime;

        @JsonProperty("type_option_shift_type")
        private String type;

        @JsonProperty("status_option_shift_approval_status")
        private String status;

        @JsonProperty("assigned_store_custom_store")
        private String assignedStore;
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

    /** Envelope for Bubble's list endpoint: {@code { "response": { ... } }}. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BubbleListResponseWrapper {
        private BubbleListResult response;
    }

    /** Bubble list payload: results plus its cursor pagination counters. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BubbleListResult {
        private List<Map<String, Object>> results = new ArrayList<>();
        /** Echo of the request cursor (offset of the first returned record). */
        private int cursor;
        /** Number of records returned in this page. */
        private int count;
        /** Records remaining after this page (0 ⇒ last page). */
        private int remaining;
    }

    /** Envelope for Bubble's single-object endpoint: {@code { "response": {...} }}. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BubbleObjectResponseWrapper {
        private Map<String, Object> response;
    }

    public String uploadFile(String filename, byte[] fileBytes) {
        try {
            log.info("Uploading file '{}' to Bubble...", filename);
            String baseApiUrl = bubbleBaseUrl.endsWith("/obj") 
                    ? bubbleBaseUrl.substring(0, bubbleBaseUrl.length() - 4) 
                    : bubbleBaseUrl;
            String uploadUrl = baseApiUrl + "/fileupload";

            String base64Content = java.util.Base64.getEncoder().encodeToString(fileBytes);

            Map<String, Object> payload = new HashMap<>();
            payload.put("name", filename);
            payload.put("contents", base64Content);
            payload.put("private", false);

            String responseStr = restClient.post()
                    .uri(uploadUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (bubbleToken != null && !bubbleToken.isEmpty() && !bubbleToken.equals("default-bubble-token")) {
                            headers.set("Authorization", "Bearer " + bubbleToken);
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            String fileUrl = null;
            if (responseStr != null) {
                responseStr = responseStr.trim();
                if (responseStr.startsWith("{")) {
                    try {
                        Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper().readValue(responseStr, Map.class);
                        if (map.containsKey("response")) {
                            fileUrl = (String) map.get("response");
                        } else if (map.containsKey("url")) {
                            fileUrl = (String) map.get("url");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse Bubble upload response as JSON, falling back to raw string: {}", responseStr);
                    }
                }
                if (fileUrl == null) {
                    fileUrl = responseStr;
                }
                if (fileUrl.startsWith("//")) {
                    fileUrl = "https:" + fileUrl;
                }
            }
            log.info("Successfully uploaded file. Bubble URL: {}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("Failed to upload file to Bubble: {}", e.getMessage(), e);
            throw new RuntimeException("Bubble upload failed: " + e.getMessage(), e);
        }
    }
}
