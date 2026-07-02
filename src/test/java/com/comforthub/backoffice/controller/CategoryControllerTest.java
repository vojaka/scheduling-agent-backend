package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.mapper.CategoryBubbleMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the two-type {@link CategoryController} Bubble proxy
 * ({@code mainproduct} tops scoped via the Company's {@code Products} list,
 * {@code category} subs via their {@code mainProduct} parent) — real mapper,
 * mocked BubbleClient, per the {@code StoreControllerTest} pattern.
 */
@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, CategoryBubbleMapper.class})
class CategoryControllerTest {

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

    private void givenCompanyWithProducts(String... mainProductIds) {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        Map<String, Object> company = new LinkedHashMap<>();
        company.put("_id", COMPANY);
        company.put("Products", List.of(mainProductIds));
        when(bubbleClient.get("company", COMPANY)).thenReturn(company);
    }

    @Test
    void getCategories_mergesMainProductsAndSubCategories() throws Exception {
        givenCompanyWithProducts("mp-1");
        Map<String, Object> mainProduct = new LinkedHashMap<>();
        mainProduct.put("_id", "mp-1");
        mainProduct.put("name", "Food");
        mainProduct.put("order", 1);
        Map<String, Object> subCategory = new LinkedHashMap<>();
        subCategory.put("_id", "cat-1");
        subCategory.put("name", "Pizza");
        subCategory.put("mainProduct", "mp-1");

        when(bubbleClient.list(eq("mainproduct"), any(), any(), any())).thenReturn(listOf(List.of(mainProduct)));
        when(bubbleClient.list(eq("category"), any(), any(), any())).thenReturn(listOf(List.of(subCategory)));

        // Sub category sorts first (null sortOrder -> 0) before the Main Product (order 1).
        mockMvc.perform(get("/api/categories").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cat-1"))
                .andExpect(jsonPath("$[0].name").value("Pizza"))
                .andExpect(jsonPath("$[0].parentId").value("mp-1"))
                .andExpect(jsonPath("$[1].id").value("mp-1"))
                .andExpect(jsonPath("$[1].name").value("Food"))
                .andExpect(jsonPath("$[1].companyId").value(COMPANY));
    }

    @Test
    void getCategories_noResolvableCompany_returnsEmptyList() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/categories").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createCategory_subUnderForeignMainProduct_isForbidden_andNothingIsWritten() throws Exception {
        givenCompanyWithProducts("mp-1");

        mockMvc.perform(post("/api/categories").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneaky Sub\",\"parentId\":\"mp-of-other-company\"}"))
                .andExpect(status().isForbidden());

        verify(bubbleClient, never()).create(any(), any());
    }

    @Test
    void deleteCategory_mainProduct_detachesFromCompanyThenDeletes() throws Exception {
        givenCompanyWithProducts("mp-1");

        mockMvc.perform(delete("/api/categories/mp-1").with(jwt()))
                .andExpect(status().isOk());

        Map<String, Object> expectedPatch = new LinkedHashMap<>();
        expectedPatch.put("Products", List.of());
        verify(bubbleClient).update("company", COMPANY, expectedPatch);
        verify(bubbleClient).delete("mainproduct", "mp-1");
    }

    @Test
    void deleteCategory_unknownId_isNotFound() throws Exception {
        givenCompanyWithProducts("mp-1");
        when(bubbleClient.get("category", "nope")).thenReturn(null);

        mockMvc.perform(delete("/api/categories/nope").with(jwt()))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).delete(any(), any());
    }
}
