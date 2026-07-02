package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.OrderBubbleMapper;
import com.comforthub.backoffice.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerOrderController.class)
@Import({SecurityConfig.class, OrderBubbleMapper.class})
class ConsumerOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private ConsumerUserService consumerUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Map<String, Object> rawOrder(String id, String customer, String status) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Merchant", "company-1");
        r.put("Store", "store-1");
        r.put("Customer (Individual)", customer);
        r.put("Type", "One Off Purchase");
        r.put("Total W VAT Order Amount", 22.5);
        r.put("S - Order Payment Status", "Paid");
        r.put("S - Order Progress Status", status);
        r.put("Created Date", 1782588208910L);
        r.put("Modified Date", 1782588208910L);
        return r;
    }

    @Test
    void getOrders_scopedToAuthenticatedCustomer_returnsPage() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        BubbleListResult result = new BubbleListResult();
        result.setResults(List.of(rawOrder("order-1", "user-1", "Preparation In progress")));
        result.setCount(1);
        result.setRemaining(0);
        // The Bubble constraints must scope by the customer, not a company.
        when(bubbleClient.list(eq("order"), contains("Customer (Individual)"),
                any(), any(), any(), eq(true))).thenReturn(result);

        mockMvc.perform(get("/api/consumer/orders").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("order-1"))
                .andExpect(jsonPath("$.content[0].customerId").value("user-1"))
                .andExpect(jsonPath("$.content[0].status").value("preparation_in_progress"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getOrder_ownOrder_returnsIt() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        when(bubbleClient.get("order", "order-1")).thenReturn(rawOrder("order-1", "user-1", "Completed"));

        mockMvc.perform(get("/api/consumer/orders/order-1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-1"))
                .andExpect(jsonPath("$.status").value("completed"));
    }

    @Test
    void getOrder_foreignCustomer_returns404() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        when(bubbleClient.get("order", "order-x")).thenReturn(rawOrder("order-x", "someone-else", "Planned"));

        mockMvc.perform(get("/api/consumer/orders/order-x").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrders_requiresJwt() throws Exception {
        mockMvc.perform(get("/api/consumer/orders"))
                .andExpect(status().isUnauthorized());
    }
}
