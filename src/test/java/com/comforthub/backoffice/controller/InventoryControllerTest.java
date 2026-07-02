package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.InventoryBubbleMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the {@link InventoryController} Bubble proxy — company
 * scoping, the Page envelope, soft-delete-aware ownership checks and the
 * inventory→offerings link read. Real mapper, mocked BubbleClient, per the
 * {@code StoreControllerTest} pattern.
 */
@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, InventoryBubbleMapper.class})
class InventoryControllerTest {

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

    /** A Bubble {@code inventory} record in the confirmed live-key shape. */
    private static Map<String, Object> rawInventory(String id, String company, boolean deleted) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Company", company);
        r.put("Name", "Trampoline");
        r.put("Is Deleted", deleted);
        r.put("Offerings", List.of("off-1", "off-2"));
        return r;
    }

    @Test
    void getInventory_scopedToCompany_returnsPageWithMappedKeys() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.list(eq("inventory"), any(), any(), any(), eq("Created Date"), eq(true)))
                .thenReturn(listOf(List.of(rawInventory("inv-1", COMPANY, false))));

        mockMvc.perform(get("/api/inventory").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("inv-1"))
                .andExpect(jsonPath("$.content[0].name").value("Trampoline"))
                .andExpect(jsonPath("$.content[0].companyId").value(COMPANY))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getInventory_noResolvableCompany_returnsEmptyPage() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inventory").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void createInventory_persistsToBubbleAndReloads() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.create(eq("inventory"), any())).thenReturn("inv-new");
        when(bubbleClient.get("inventory", "inv-new")).thenReturn(rawInventory("inv-new", COMPANY, false));

        mockMvc.perform(post("/api/inventory").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trampoline\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("inv-new"))
                .andExpect(jsonPath("$.name").value("Trampoline"));
    }

    @Test
    void updateInventory_foreignCompany_returns404_andDoesNotWrite() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.get("inventory", "inv-x")).thenReturn(rawInventory("inv-x", "other-company", false));

        mockMvc.perform(put("/api/inventory/inv-x").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijack\"}"))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).update(eq("inventory"), eq("inv-x"), any());
    }

    @Test
    void updateInventory_softDeletedRecord_returns404() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.get("inventory", "inv-1")).thenReturn(rawInventory("inv-1", COMPANY, true));

        mockMvc.perform(put("/api/inventory/inv-1").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zombie edit\"}"))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).update(any(), any(), any());
    }

    @Test
    void getLinkedOfferings_returnsOfferingIdsOfOwnedItem() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.get("inventory", "inv-1")).thenReturn(rawInventory("inv-1", COMPANY, false));

        mockMvc.perform(get("/api/inventory/inv-1/offerings").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("off-1"))
                .andExpect(jsonPath("$[1]").value("off-2"));
    }
}
