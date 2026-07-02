package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.consumer.mapper.CartItemBubbleMapper;
import com.comforthub.backoffice.security.SecurityConfig;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerCartController.class)
@Import({SecurityConfig.class, CartItemBubbleMapper.class})
class ConsumerCartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private ConsumerUserService consumerUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Map<String, Object> rawCartItem(String id, String cartId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Cart", cartId);
        r.put("Inventory", "inv-1");
        r.put("Offering", "off-1");
        r.put("Order", "order-1");
        r.put("Store (single)", "store-1");
        r.put("Quantity", 2);
        r.put("Cart Item Status", "Active");
        r.put("Type", "One Off Purchase");
        r.put("is Deleted", false);
        r.put("1 pcs - Total Cost - W VAT", 5.0);
        r.put("Total Cart Item Cost W VAT", 10.0);
        r.put("Total Cart Item VAT", 2.0);
        return r;
    }

    private void authenticatedUser() {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        when(consumerUserService.currentCartId()).thenReturn(Optional.of("cart-1"));
    }

    @Test
    void getCart_returnsUsersActiveLines() throws Exception {
        authenticatedUser();
        BubbleListResult result = new BubbleListResult();
        result.setResults(List.of(rawCartItem("ci-1", "cart-1")));
        when(bubbleClient.list(eq("cartitem"), any(), any(), any())).thenReturn(result);

        mockMvc.perform(get("/api/consumer/cart").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").value("cart-1"))
                .andExpect(jsonPath("$.items[0].id").value("ci-1"))
                .andExpect(jsonPath("$.items[0].inventoryId").value("inv-1"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].totalWithVat").value(10.0))
                .andExpect(jsonPath("$.totalWithVat").value(10.0));
    }

    @Test
    void getCart_requiresJwt() throws Exception {
        mockMvc.perform(get("/api/consumer/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addItem_runsBubbleWorkflow_pinnedToAuthenticatedUser() throws Exception {
        authenticatedUser();
        when(bubbleClient.runWorkflow(anyString(), any())).thenReturn(Map.of());
        when(bubbleClient.list(eq("cartitem"), any(), any(), any())).thenReturn(new BubbleListResult());

        mockMvc.perform(post("/api/consumer/cart/items").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryId\":\"inv-1\",\"offeringId\":\"off-1\",\"quantity\":2,\"storeId\":\"store-1\"}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(bubbleClient).runWorkflow(eq("adding_to_cart_attributes(be)"), params.capture());
        assertThat(params.getValue())
                .containsEntry("inventory", "inv-1")
                .containsEntry("offering", "off-1")
                .containsEntry("quantity", 2)
                .containsEntry("store", "store-1")
                // customer/added_by come from the JWT-resolved user, never the body.
                .containsEntry("customer_individual", "user-1")
                .containsEntry("added_by", "user-1");
    }

    @Test
    void addItem_missingRequiredFields_returnsBadRequest() throws Exception {
        authenticatedUser();

        mockMvc.perform(post("/api/consumer/cart/items").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());

        verify(bubbleClient, never()).runWorkflow(anyString(), any());
    }

    @Test
    void updateItem_foreignCart_returns404_andDoesNotWrite() throws Exception {
        authenticatedUser();
        when(bubbleClient.get("cartitem", "ci-x")).thenReturn(rawCartItem("ci-x", "other-cart"));

        mockMvc.perform(patch("/api/consumer/cart/items/ci-x").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).update(eq("cartitem"), any(), any());
        verify(bubbleClient, never()).runWorkflow(anyString(), any());
    }

    @Test
    void updateItem_ownedLine_patchesQuantityAndRecalcs() throws Exception {
        authenticatedUser();
        when(bubbleClient.get("cartitem", "ci-1")).thenReturn(rawCartItem("ci-1", "cart-1"));
        when(bubbleClient.runWorkflow(anyString(), any())).thenReturn(Map.of());

        mockMvc.perform(patch("/api/consumer/cart/items/ci-1").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ci-1"));

        verify(bubbleClient).update("cartitem", "ci-1", Map.of("Quantity", 3));
        verify(bubbleClient).runWorkflow(eq("cart_item_recalc"), any());
    }

    @Test
    void deleteItem_ownedLine_runsDeleteWorkflow() throws Exception {
        authenticatedUser();
        when(bubbleClient.get("cartitem", "ci-1")).thenReturn(rawCartItem("ci-1", "cart-1"));
        when(bubbleClient.runWorkflow(anyString(), any())).thenReturn(Map.of());

        mockMvc.perform(delete("/api/consumer/cart/items/ci-1").with(jwt()))
                .andExpect(status().isNoContent());

        verify(bubbleClient).runWorkflow(eq("delete_cart_item"), eq(Map.of("cart_item", "ci-1")));
    }
}
