package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.AvailabilityBubbleMapper;
import com.comforthub.backoffice.mapper.StoreBubbleMapper;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the from-scratch {@link AvailabilityController}: company
 * scoping via the thing (store record / worker mirror), the raw-key mapping, and
 * the upsert PUT (update-by-id, lazy-create, and reuse-existing-to-avoid-dupes).
 */
@WebMvcTest(AvailabilityController.class)
@Import({SecurityConfig.class, AvailabilityBubbleMapper.class, StoreBubbleMapper.class})
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private BubbleUserRepository userRepository;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Map<String, Object> rawStore(String id, String company) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("company__single__custom____merchant", company);
        r.put("isdeleted_boolean", false);
        return r;
    }

    private static Map<String, Object> rawAvailability(String id, String thingType, String thingId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("thing_option_things", thingType);
        r.put("thing_id_text", thingId);
        r.put("usual_available_days_list_option_calendar_days", List.of("Monday", "Tuesday"));
        r.put("workday_availability___start_number", 9);
        r.put("workday_availability___end_number", 17);
        r.put("weekend_availability___start_number", 10);
        r.put("weekend_availability___end_number", 16);
        return r;
    }

    private static BubbleListResult listOf(Map<String, Object>... records) {
        BubbleListResult result = new BubbleListResult();
        result.setResults(List.of(records));
        result.setCount(records.length);
        return result;
    }

    @Test
    void getAvailability_ownedStore_returnsMappedProfile() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        when(bubbleClient.get("store", "store-1")).thenReturn(rawStore("store-1", "c1"));
        when(bubbleClient.list(eq("availability"), any(), eq(0), eq(1)))
                .thenReturn(listOf(rawAvailability("avail-1", "Store", "store-1")));

        mockMvc.perform(get("/api/availability?thingType=Store&thingId=store-1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("avail-1"))
                .andExpect(jsonPath("$.thingType").value("Store"))
                .andExpect(jsonPath("$.thingId").value("store-1"))
                .andExpect(jsonPath("$.availableDays[0]").value("Monday"))
                .andExpect(jsonPath("$.workdayStartHour").value(9))
                .andExpect(jsonPath("$.weekendEndHour").value(16));
    }

    @Test
    void getAvailability_foreignStore_returns404() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        when(bubbleClient.get("store", "store-x")).thenReturn(rawStore("store-x", "other"));

        mockMvc.perform(get("/api/availability?thingType=Store&thingId=store-x").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvailability_ownedButNoProfileYet_returns404() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        when(bubbleClient.get("store", "store-1")).thenReturn(rawStore("store-1", "c1"));
        when(bubbleClient.list(eq("availability"), any(), eq(0), eq(1))).thenReturn(new BubbleListResult());

        mockMvc.perform(get("/api/availability?thingType=Store&thingId=store-1").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_existingRecord_updatesInPlace() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        when(bubbleClient.get("availability", "avail-1"))
                .thenReturn(rawAvailability("avail-1", "Store", "store-1"));
        when(bubbleClient.get("store", "store-1")).thenReturn(rawStore("store-1", "c1"));

        mockMvc.perform(put("/api/availability/avail-1").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workdayStartHour\":8,\"availableDays\":[\"Monday\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("avail-1"));

        verify(bubbleClient).update(eq("availability"), eq("avail-1"), any());
        verify(bubbleClient, never()).create(eq("availability"), any());
    }

    @Test
    void put_noExistingId_lazyCreatesForOwnedWorker() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        when(bubbleClient.get("availability", "new")).thenReturn(null);
        BubbleUserEntity worker = new BubbleUserEntity();
        worker.setBubbleId("user-1");
        worker.setCompanyId("c1");
        when(userRepository.findByBubbleId("user-1")).thenReturn(Optional.of(worker));
        when(bubbleClient.list(eq("availability"), any(), eq(0), eq(1))).thenReturn(new BubbleListResult());
        when(bubbleClient.create(eq("availability"), any())).thenReturn("avail-new");
        when(bubbleClient.get("availability", "avail-new"))
                .thenReturn(rawAvailability("avail-new", "Worker", "user-1"));

        mockMvc.perform(put("/api/availability/new").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thingType\":\"Worker\",\"thingId\":\"user-1\",\"workdayStartHour\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("avail-new"))
                .andExpect(jsonPath("$.thingType").value("Worker"));

        verify(bubbleClient).create(eq("availability"), any());
    }

    @Test
    void put_noExistingId_reusesThingsProfileInsteadOfDuplicating() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        when(bubbleClient.get("availability", "new")).thenReturn(null);
        when(bubbleClient.get("store", "store-1")).thenReturn(rawStore("store-1", "c1"));
        when(bubbleClient.list(eq("availability"), any(), eq(0), eq(1)))
                .thenReturn(listOf(rawAvailability("avail-1", "Store", "store-1")));
        // Read-back after the in-place update reflects the persisted record.
        when(bubbleClient.get("availability", "avail-1"))
                .thenReturn(rawAvailability("avail-1", "Store", "store-1"));

        mockMvc.perform(put("/api/availability/new").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thingType\":\"Store\",\"thingId\":\"store-1\",\"workdayStartHour\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("avail-1"));

        // Upsert, not duplicate: update the existing profile, never create a new one.
        verify(bubbleClient).update(eq("availability"), eq("avail-1"), any());
        verify(bubbleClient, never()).create(eq("availability"), any());
    }

    @Test
    void put_noCompany_returns403() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/availability/whatever").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thingType\":\"Store\",\"thingId\":\"store-1\"}"))
                .andExpect(status().isForbidden());
    }
}
