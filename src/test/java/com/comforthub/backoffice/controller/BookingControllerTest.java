package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.BookingBubbleMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the {@link BookingController} Bubble proxy (following the
 * {@code StoreControllerTest} pattern — real mapper, mocked BubbleClient):
 * indirect company scoping through the company's inventories, and the #115
 * create-time validation that rejects foreign Service/Worker references.
 */
@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, BookingBubbleMapper.class})
class BookingControllerTest {

    private static final String COMPANY = "company-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private BubbleUserRepository userRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static BubbleListResult listOf(List<Map<String, Object>> results) {
        BubbleListResult r = new BubbleListResult();
        r.setResults(results);
        r.setCount(results.size());
        r.setRemaining(0);
        return r;
    }

    /** A Bubble {@code events} record in the confirmed live-key shape. */
    private static Map<String, Object> rawEvent(String id) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Name", "Massage");
        r.put("Date - Date Range Start", 1751356800000L);
        r.put("Date - Date Range End", 1751364000000L);
        r.put("Worker", "worker-1");
        r.put("Service", "inv-1");
        return r;
    }

    private void givenCompanyWithInventory() {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.list(eq("inventory"), any(), any(), any()))
                .thenReturn(listOf(List.of(Map.of("_id", "inv-1"))));
    }

    private void givenCompanyWorkers() {
        BubbleUserEntity worker = new BubbleUserEntity();
        worker.setBubbleId("worker-1");
        worker.setCompanyId(COMPANY);
        when(userRepository.findByCompanyId(COMPANY)).thenReturn(List.of(worker));
    }

    // ------------------------------------------------------------------- GET

    @Test
    void getBookings_scopedThroughCompanyInventories_mapsConfirmedKeys() throws Exception {
        givenCompanyWithInventory();
        when(bubbleClient.list(eq("events"), any(), any(), any(), eq("Created Date"), eq(true)))
                .thenReturn(listOf(List.of(rawEvent("evt-1"))));

        mockMvc.perform(get("/api/bookings").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("evt-1"))
                .andExpect(jsonPath("$.content[0].title").value("Massage"))
                .andExpect(jsonPath("$.content[0].workerId").value("worker-1"))
                .andExpect(jsonPath("$.content[0].serviceId").value("inv-1"))
                .andExpect(jsonPath("$.content[0].companyId").value(COMPANY))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getBookings_noResolvableCompany_returnsEmptyPage() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/bookings").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ----------------------------------------------------------- POST (#115)

    @Test
    void createBooking_foreignServiceRef_isRejected_andNothingIsWritten() throws Exception {
        givenCompanyWithInventory();

        mockMvc.perform(post("/api/bookings").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\",\"serviceId\":\"inv-of-other-company\"}"))
                .andExpect(status().isBadRequest());

        verify(bubbleClient, never()).create(any(), any());
    }

    @Test
    void createBooking_foreignWorkerRef_isRejected_andNothingIsWritten() throws Exception {
        givenCompanyWithInventory();
        givenCompanyWorkers();

        mockMvc.perform(post("/api/bookings").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\",\"serviceId\":\"inv-1\",\"workerId\":\"worker-of-other-company\"}"))
                .andExpect(status().isBadRequest());

        verify(bubbleClient, never()).create(any(), any());
    }

    @Test
    void createBooking_ownRefs_createsInBubbleWithServiceScope() throws Exception {
        givenCompanyWithInventory();
        givenCompanyWorkers();
        when(bubbleClient.create(eq("events"), any())).thenReturn("evt-new");
        when(bubbleClient.get("events", "evt-new")).thenReturn(rawEvent("evt-new"));

        mockMvc.perform(post("/api/bookings").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Massage\",\"serviceId\":\"inv-1\",\"workerId\":\"worker-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("evt-new"))
                .andExpect(jsonPath("$.serviceId").value("inv-1"))
                .andExpect(jsonPath("$.companyId").value(COMPANY));

        verify(bubbleClient).create(eq("events"), any());
    }

    @Test
    void createBooking_noResolvableCompany_isForbidden() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/bookings").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\"}"))
                .andExpect(status().isForbidden());

        verify(bubbleClient, never()).create(any(), any());
    }
}
