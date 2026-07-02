package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.StockBubbleMapper;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
 * Contract tests for the {@link StockController} Bubble proxy — stock has no
 * company field, so scoping goes indirectly through the company's stores; the
 * quantity update is an upsert on the store+inventory pair. Real mapper, mocked
 * BubbleClient, per the {@code StoreControllerTest} pattern.
 */
@WebMvcTest(StockController.class)
@Import({SecurityConfig.class, StockBubbleMapper.class})
class StockControllerTest {

    private static final String COMPANY = "company-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static BubbleListResult listOf(List<Map<String, Object>> results) {
        BubbleListResult r = new BubbleListResult();
        r.setResults(results);
        r.setCount(results.size());
        r.setRemaining(0);
        return r;
    }

    private static Map<String, Object> rawStock(String id, String storeId, String inventoryId, int quantity) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Store", storeId);
        r.put("Inventory", inventoryId);
        r.put("Qnty in stock", quantity);
        return r;
    }

    private void givenCompanyStores(String... storeIds) {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        List<Map<String, Object>> stores = new java.util.ArrayList<>();
        for (String id : storeIds) {
            stores.add(Map.of("_id", id));
        }
        when(bubbleClient.list(eq("store"), any(), any(), any())).thenReturn(listOf(stores));
    }

    @Test
    void getStock_scopedThroughCompanyStores_mapsConfirmedKeys() throws Exception {
        givenCompanyStores("store-1");
        when(bubbleClient.list(eq("stock"), any(), any(), any(), eq("Created Date"), eq(true)))
                .thenReturn(listOf(List.of(rawStock("stock-1", "store-1", "inv-1", 5))));

        mockMvc.perform(get("/api/stock").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("stock-1"))
                .andExpect(jsonPath("$.content[0].storeId").value("store-1"))
                .andExpect(jsonPath("$.content[0].inventoryId").value("inv-1"))
                .andExpect(jsonPath("$.content[0].quantity").value(5))
                .andExpect(jsonPath("$.content[0].companyId").value(COMPANY));
    }

    @Test
    void getStock_foreignStoreFilter_returnsEmpty_withoutQueryingStock() throws Exception {
        givenCompanyStores("store-1");

        mockMvc.perform(get("/api/stock").param("storeId", "store-of-other-company").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(bubbleClient, never()).list(eq("stock"), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void updateQuantity_existingRow_patchesQuantity() throws Exception {
        givenCompanyStores("store-1");
        when(bubbleClient.list(eq("stock"), any(), any(), any()))
                .thenReturn(listOf(List.of(rawStock("stock-1", "store-1", "inv-1", 5))));
        when(bubbleClient.get("stock", "stock-1")).thenReturn(rawStock("stock-1", "store-1", "inv-1", 42));

        mockMvc.perform(put("/api/stock/store-1/inv-1").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(42));

        Map<String, Object> expectedPatch = new LinkedHashMap<>();
        expectedPatch.put("Qnty in stock", 42);
        verify(bubbleClient).update("stock", "stock-1", expectedPatch);
        verify(bubbleClient, never()).create(any(), any());
    }

    @Test
    void updateQuantity_missingRow_createsIt() throws Exception {
        givenCompanyStores("store-1");
        when(bubbleClient.list(eq("stock"), any(), any(), any())).thenReturn(listOf(List.of()));
        when(bubbleClient.create(eq("stock"), any())).thenReturn("stock-new");
        when(bubbleClient.get("stock", "stock-new")).thenReturn(rawStock("stock-new", "store-1", "inv-1", 7));

        mockMvc.perform(put("/api/stock/store-1/inv-1").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("stock-new"))
                .andExpect(jsonPath("$.quantity").value(7));

        Map<String, Object> expectedCreate = new LinkedHashMap<>();
        expectedCreate.put("Store", "store-1");
        expectedCreate.put("Inventory", "inv-1");
        expectedCreate.put("Qnty in stock", 7);
        verify(bubbleClient).create("stock", expectedCreate);
    }

    @Test
    void updateQuantity_foreignStore_returns404_andDoesNotWrite() throws Exception {
        givenCompanyStores("store-1");

        mockMvc.perform(put("/api/stock/store-of-other-company/inv-1").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":42}"))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).update(any(), any(), any());
        verify(bubbleClient, never()).create(any(), any());
    }
}
