package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.InventoryBubbleMapper;
import com.comforthub.backoffice.mapper.OfferingBubbleMapper;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the {@link OfferingController} Bubble proxy — company
 * scoping, the bidirectional inventory↔offering assign/unassign link, and the
 * new {@code deliveryTypes} pass-through ("Delivery Types", UI #32). Real
 * mappers, mocked BubbleClient, per the {@code StoreControllerTest} pattern.
 */
@WebMvcTest(OfferingController.class)
@Import({SecurityConfig.class, OfferingBubbleMapper.class, InventoryBubbleMapper.class})
class OfferingControllerTest {

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

    private static Map<String, Object> rawOffering(String id, String company) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Belongs to", company);
        r.put("Offering name", "Basic");
        r.put("Offering Activity Status", "Active");
        r.put("Delivery Types", List.of("Courier delivery", "Self pick up"));
        return r;
    }

    private static Map<String, Object> rawInventory(String id, String company) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Company", company);
        r.put("Name", "Trampoline");
        r.put("Is Deleted", false);
        return r;
    }

    @Test
    void getOfferings_scopedToCompany_mapsDeliveryTypesList() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.list(eq("offerings"), any(), any(), any(), eq("Created Date"), eq(true)))
                .thenReturn(listOf(List.of(rawOffering("off-1", COMPANY))));

        mockMvc.perform(get("/api/offerings").param("status", "Active").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("off-1"))
                .andExpect(jsonPath("$.content[0].name").value("Basic"))
                .andExpect(jsonPath("$.content[0].status").value("Active"))
                .andExpect(jsonPath("$.content[0].deliveryTypes[0]").value("Courier delivery"))
                .andExpect(jsonPath("$.content[0].deliveryTypes[1]").value("Self pick up"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createOffering_passesDeliveryTypesVerbatimToBubble() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.create(eq("offerings"), any())).thenReturn("off-new");
        when(bubbleClient.get("offerings", "off-new")).thenReturn(rawOffering("off-new", COMPANY));

        mockMvc.perform(post("/api/offerings").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Basic\",\"deliveryTypes\":[\"Courier delivery\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("off-new"));

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bubbleClient).create(eq("offerings"), bodyCaptor.capture());
        Map<String, Object> body = (Map<String, Object>) bodyCaptor.getValue();
        // Option-set values pass through verbatim under the verified list key.
        assertThat(body.get("Delivery Types")).isEqualTo(List.of("Courier delivery"));
        assertThat(body.get("Belongs to")).isEqualTo(COMPANY);
    }

    @Test
    void assign_linksBothSidesOfTheInventoryOfferingLink() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.get("offerings", "off-1")).thenReturn(rawOffering("off-1", COMPANY));
        when(bubbleClient.get("inventory", "inv-1")).thenReturn(rawInventory("inv-1", COMPANY));

        mockMvc.perform(post("/api/offerings/off-1/assign").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryId\":\"inv-1\"}"))
                .andExpect(status().isOk());

        Map<String, Object> offeringPatch = new LinkedHashMap<>();
        offeringPatch.put("Inventory", List.of("inv-1"));
        verify(bubbleClient).update("offerings", "off-1", offeringPatch);

        Map<String, Object> inventoryPatch = new LinkedHashMap<>();
        inventoryPatch.put("Offerings", List.of("off-1"));
        verify(bubbleClient).update("inventory", "inv-1", inventoryPatch);
    }

    @Test
    void assign_foreignInventory_returns404_andDoesNotWrite() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.get("offerings", "off-1")).thenReturn(rawOffering("off-1", COMPANY));
        when(bubbleClient.get("inventory", "inv-x")).thenReturn(rawInventory("inv-x", "other-company"));

        mockMvc.perform(post("/api/offerings/off-1/assign").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryId\":\"inv-x\"}"))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).update(any(), any(), any());
    }

    @Test
    void unassign_removesIdFromBothSides() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        Map<String, Object> offering = rawOffering("off-1", COMPANY);
        offering.put("Inventory", List.of("inv-1"));
        Map<String, Object> inventory = rawInventory("inv-1", COMPANY);
        inventory.put("Offerings", List.of("off-1"));
        when(bubbleClient.get("offerings", "off-1")).thenReturn(offering);
        when(bubbleClient.get("inventory", "inv-1")).thenReturn(inventory);

        mockMvc.perform(delete("/api/offerings/off-1/assign").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryId\":\"inv-1\"}"))
                .andExpect(status().isOk());

        Map<String, Object> offeringPatch = new LinkedHashMap<>();
        offeringPatch.put("Inventory", List.of());
        verify(bubbleClient).update("offerings", "off-1", offeringPatch);

        Map<String, Object> inventoryPatch = new LinkedHashMap<>();
        inventoryPatch.put("Offerings", List.of());
        verify(bubbleClient).update("inventory", "inv-1", inventoryPatch);
    }

    @Test
    void updateOffering_foreignCompany_returns404_andDoesNotWrite() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(bubbleClient.get("offerings", "off-x")).thenReturn(rawOffering("off-x", "other-company"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/offerings/off-x").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hijack\"}"))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).update(any(), any(), any());
    }
}
