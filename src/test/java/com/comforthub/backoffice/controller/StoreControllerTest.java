package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.StoreBubbleMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the from-scratch {@link StoreController} Bubble proxy:
 * company scoping, the Page envelope (preserving the old {@code /api/stores}
 * shape), and that reads/writes use the VERIFIED raw Bubble field keys
 * ({@code store_name_text} etc.) via the real {@link StoreBubbleMapper}.
 */
@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, StoreBubbleMapper.class})
class StoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    /** A Bubble {@code store} record in the raw-key shape the live Data API returns. */
    private static Map<String, Object> rawStore(String id, String company, boolean deleted) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("store_name_text", "Downtown");
        r.put("company__single__custom____merchant", company);
        r.put("availability_custom_worker_availability", "avail-1");
        r.put("isdeleted_boolean", deleted);
        return r;
    }

    @Test
    void getStores_scopedToCompany_returnsPageWithMappedRawKeys() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        BubbleListResult result = new BubbleListResult();
        result.setResults(List.of(rawStore("store-1", "company-1", false)));
        result.setCount(1);
        result.setRemaining(0);
        when(bubbleClient.list(eq("store"), any(), any(), any(), any(), eq(false))).thenReturn(result);

        mockMvc.perform(get("/api/stores").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("store-1"))
                .andExpect(jsonPath("$.content[0].bubbleId").value("store-1"))
                .andExpect(jsonPath("$.content[0].name").value("Downtown"))
                .andExpect(jsonPath("$.content[0].companyId").value("company-1"))
                .andExpect(jsonPath("$.content[0].availabilityId").value("avail-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getStores_noResolvableCompany_returnsEmptyPage() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/stores").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void createStore_persistsToBubbleAndReloads() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.create(eq("store"), any())).thenReturn("store-new");
        when(bubbleClient.get("store", "store-new")).thenReturn(rawStore("store-new", "company-1", false));

        mockMvc.perform(post("/api/stores").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Downtown\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("store-new"))
                .andExpect(jsonPath("$.name").value("Downtown"));
    }

    @Test
    void updateStore_foreignCompany_returns404_andDoesNotWrite() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.get("store", "store-x")).thenReturn(rawStore("store-x", "other-company", false));

        mockMvc.perform(put("/api/stores/store-x").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).update(eq("store"), eq("store-x"), any());
    }

    @Test
    void deleteStore_ownedRecord_softDeletesViaIsDeletedFlag() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.get("store", "store-1")).thenReturn(rawStore("store-1", "company-1", false));

        mockMvc.perform(delete("/api/stores/store-1").with(jwt()))
                .andExpect(status().isOk());

        // Soft-delete: PATCH isdeleted_boolean = true, never a hard delete().
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("isdeleted_boolean", true);
        verify(bubbleClient).update("store", "store-1", expected);
        verify(bubbleClient, never()).delete(eq("store"), any());
    }
}
