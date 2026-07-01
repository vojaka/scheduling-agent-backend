package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.OrderBubbleMapper;
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

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, OrderBubbleMapper.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Map<String, Object> rawOrder(String id, String company, String status, String type, String paymentStatus) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Merchant", company);
        r.put("Store", "store-1");
        r.put("Customer (Individual)", "customer-1");
        r.put("Type", type);
        r.put("Total W VAT Order Amount", 22.5);
        r.put("S - Order Payment Status", paymentStatus);
        r.put("S - Order Progress Status", status);
        r.put("Created Date", 1782588208910L);
        r.put("Modified Date", 1782588208910L);
        return r;
    }

    @Test
    void getOrders_scopedToCompany_returnsPage() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        BubbleListResult result = new BubbleListResult();
        result.setResults(List.of(rawOrder("order-1", "company-1", "Preparation In progress", "One Off Purchase", "Paid")));
        result.setCount(1);
        result.setRemaining(0);
        when(bubbleClient.list(eq("order"), any(), any(), any(), any(), eq(true))).thenReturn(result);

        mockMvc.perform(get("/api/orders").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("order-1"))
                .andExpect(jsonPath("$.content[0].companyId").value("company-1"))
                .andExpect(jsonPath("$.content[0].storeId").value("store-1"))
                .andExpect(jsonPath("$.content[0].customerId").value("customer-1"))
                .andExpect(jsonPath("$.content[0].type").value("One Off Purchase"))
                .andExpect(jsonPath("$.content[0].amount").value(22.5))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("Paid"))
                .andExpect(jsonPath("$.content[0].status").value("preparation_in_progress"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createOrder_persistsToBubble_andMapsUnpaidToNull() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.create(eq("order"), any())).thenReturn("order-new");
        when(bubbleClient.get("order", "order-new")).thenReturn(rawOrder("order-new", "company-1", "Not started", "One Off Purchase", null));

        mockMvc.perform(post("/api/orders").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"store-1\",\"customerId\":\"customer-1\",\"type\":\"One Off Purchase\",\"amount\":22.5,\"paymentStatus\":\"Unpaid\",\"status\":\"not_started\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-new"))
                .andExpect(jsonPath("$.paymentStatus").doesNotExist());

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("Merchant", "company-1");
        expected.put("Store", "store-1");
        expected.put("Customer (Individual)", "customer-1");
        expected.put("Type", "One Off Purchase");
        expected.put("Total W VAT Order Amount", new BigDecimal("22.5"));
        expected.put("S - Order Payment Status", null);
        expected.put("S - Order Progress Status", "Not started");

        verify(bubbleClient).create("order", expected);
    }

    @Test
    void updateOrder_ownedRecord_persistsAndReloads() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.get("order", "order-1")).thenReturn(rawOrder("order-1", "company-1", "Preparation In progress", "One Off Purchase", "Partial"));

        mockMvc.perform(put("/api/orders/order-1").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"store-1\",\"customerId\":\"customer-1\",\"type\":\"One Off Purchase\",\"amount\":22.5,\"paymentStatus\":\"Partial\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-1"))
                .andExpect(jsonPath("$.paymentStatus").value("Partial"));

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("Store", "store-1");
        expected.put("Customer (Individual)", "customer-1");
        expected.put("Type", "One Off Purchase");
        expected.put("Total W VAT Order Amount", new BigDecimal("22.5"));
        expected.put("S - Order Payment Status", "Partial");

        verify(bubbleClient).update("order", "order-1", expected);
    }

    @Test
    void updateOrder_foreignCompany_returns404_andDoesNotWrite() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.get("order", "order-x")).thenReturn(rawOrder("order-x", "other-company", "Not started", "One Off Purchase", "Paid"));

        mockMvc.perform(put("/api/orders/order-x").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"store-1\",\"type\":\"One Off Purchase\",\"amount\":22.5,\"paymentStatus\":\"Paid\"}"))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).update(eq("order"), eq("order-x"), any());
    }

    @Test
    void updateStatus_ownedRecord_updatesStatusAndReloads() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.get("order", "order-1")).thenReturn(rawOrder("order-1", "company-1", "Preparation In progress", "One Off Purchase", "Paid"));

        mockMvc.perform(patch("/api/orders/order-1/status").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"planned\"}"))
                .andExpect(status().isOk());

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("S - Order Progress Status", "Planned");

        verify(bubbleClient).update("order", "order-1", expected);
    }

    @Test
    void updateStatus_invalidStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/orders/order-1/status").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"invalid_status\"}"))
                .andExpect(status().isBadRequest());

        verify(bubbleClient, never()).update(eq("order"), any(), any());
    }
}
