package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.consumer.mapper.ConsumerCatalogScope;
import com.comforthub.backoffice.mapper.CategoryBubbleMapper;
import com.comforthub.backoffice.mapper.InventoryBubbleMapper;
import com.comforthub.backoffice.mapper.OfferingBubbleMapper;
import com.comforthub.backoffice.mapper.StockBubbleMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerCatalogController.class)
@Import({SecurityConfig.class, CategoryBubbleMapper.class, InventoryBubbleMapper.class,
        OfferingBubbleMapper.class, StockBubbleMapper.class, ConsumerCatalogScope.class})
class ConsumerCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static BubbleListResult resultOf(List<Map<String, Object>> records) {
        BubbleListResult result = new BubbleListResult();
        result.setResults(records);
        result.setCount(records.size());
        return result;
    }

    @Test
    void getCategories_buildsTreeFromMainProductsAndCategories() throws Exception {
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put("_id", "mp-1");
        mp.put("name", "Meals");
        mp.put("order", 1);
        Map<String, Object> cat = new LinkedHashMap<>();
        cat.put("_id", "cat-1");
        cat.put("name", "Soups");
        cat.put("mainProduct", "mp-1");

        when(bubbleClient.list(eq("mainproduct"), any(), any(), any())).thenReturn(resultOf(List.of(mp)));
        when(bubbleClient.list(eq("category"), any(), any(), any())).thenReturn(resultOf(List.of(cat)));

        mockMvc.perform(get("/api/consumer/catalog/categories").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mp-1"))
                .andExpect(jsonPath("$[0].name").value("Meals"))
                .andExpect(jsonPath("$[0].children[0].id").value("cat-1"))
                .andExpect(jsonPath("$[0].children[0].name").value("Soups"));
    }

    @Test
    void getProducts_enrichesWithOfferingsAndStock() throws Exception {
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("_id", "prod-1");
        inv.put("Name", "Tomato Soup");
        inv.put("Price Base (w VAT)", 4.5);
        inv.put("Is Deleted", false);
        inv.put("Offerings", List.of("off-1"));

        Map<String, Object> off = new LinkedHashMap<>();
        off.put("_id", "off-1");
        off.put("Offering name", "Default");
        off.put("Q - Unlimited Quantity", false);

        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("_id", "st-1");
        stock.put("Store", "store-1");
        stock.put("Inventory", "prod-1");
        stock.put("Qnty in stock", 5);

        when(bubbleClient.list(eq("inventory"), any(), any(), any(), any(), eq(true)))
                .thenReturn(resultOf(List.of(inv)));
        when(bubbleClient.list(eq("offerings"), any(), any(), any())).thenReturn(resultOf(List.of(off)));
        when(bubbleClient.list(eq("stock"), any(), any(), any())).thenReturn(resultOf(List.of(stock)));

        mockMvc.perform(get("/api/consumer/catalog/products").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].product.id").value("prod-1"))
                .andExpect(jsonPath("$[0].product.name").value("Tomato Soup"))
                .andExpect(jsonPath("$[0].offerings[0].id").value("off-1"))
                .andExpect(jsonPath("$[0].stock[0].quantity").value(5))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void getProducts_filtersOnlyActiveRecords() throws Exception {
        when(bubbleClient.list(eq("inventory"), contains("Is Deleted"), any(), any(), any(), eq(true)))
                .thenReturn(resultOf(List.of()));

        mockMvc.perform(get("/api/consumer/catalog/products").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getProduct_softDeleted_returns404() throws Exception {
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("_id", "prod-x");
        inv.put("Is Deleted", true);
        when(bubbleClient.get("inventory", "prod-x")).thenReturn(inv);

        mockMvc.perform(get("/api/consumer/catalog/products/prod-x").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void catalog_requiresJwt() throws Exception {
        mockMvc.perform(get("/api/consumer/catalog/products"))
                .andExpect(status().isUnauthorized());
    }
}
