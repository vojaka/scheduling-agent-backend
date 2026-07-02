package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.consumer.mapper.AddressBubbleMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerAddressController.class)
@Import({SecurityConfig.class, AddressBubbleMapper.class})
class ConsumerAddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private ConsumerUserService consumerUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Map<String, Object> rawAddress(String id, String owner) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("_id", id);
        r.put("Owner (Individual)", owner);
        r.put("Street", "Pikk");
        r.put("House number", "7");
        r.put("City", "Tallinn");
        r.put("Post Code", "10123");
        r.put("Country(string)", "Estonia");
        r.put("Primary?", true);
        r.put("isDeleted", false);
        return r;
    }

    @Test
    void getAddresses_returnsUsersActiveAddresses() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        BubbleListResult result = new BubbleListResult();
        result.setResults(List.of(rawAddress("addr-1", "user-1")));
        when(bubbleClient.list(eq("address"), any(), any(), any())).thenReturn(result);

        mockMvc.perform(get("/api/consumer/addresses").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("addr-1"))
                .andExpect(jsonPath("$[0].street").value("Pikk"))
                .andExpect(jsonPath("$[0].primary").value(true));
    }

    @Test
    void createAddress_runsSaveAddressWorkflow_forAuthenticatedUser() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        when(bubbleClient.runWorkflow(anyString(), any())).thenReturn(Map.of());
        when(bubbleClient.list(eq("address"), any(), any(), any())).thenReturn(new BubbleListResult());

        mockMvc.perform(post("/api/consumer/addresses").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"Pikk\",\"houseNumber\":\"7\",\"city\":\"Tallinn\","
                                + "\"propertyType\":\"Apartment\",\"primary\":true,"
                                + "\"shortString\":\"Pikk 7, Tallinn\"}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(bubbleClient).runWorkflow(eq("save_address"), params.capture());
        assertThat(params.getValue())
                .containsEntry("user", "user-1")
                .containsEntry("is_user_address", true)
                .containsEntry("street_name", "Pikk")
                .containsEntry("house_nr", "7")
                .containsEntry("primary", true)
                .containsEntry("address_short_string", "Pikk 7, Tallinn");
    }

    @Test
    void deleteAddress_owned_runsDeleteWorkflow() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        when(bubbleClient.get("address", "addr-1")).thenReturn(rawAddress("addr-1", "user-1"));
        when(bubbleClient.runWorkflow(anyString(), any())).thenReturn(Map.of());

        mockMvc.perform(delete("/api/consumer/addresses/addr-1").with(jwt()))
                .andExpect(status().isNoContent());

        verify(bubbleClient).runWorkflow(eq("delete_address"),
                eq(Map.of("address", "addr-1", "user", "user-1")));
    }

    @Test
    void deleteAddress_foreignOwner_returns404_andDoesNotDelete() throws Exception {
        when(consumerUserService.currentBubbleUserId()).thenReturn(Optional.of("user-1"));
        when(bubbleClient.get("address", "addr-x")).thenReturn(rawAddress("addr-x", "someone-else"));

        mockMvc.perform(delete("/api/consumer/addresses/addr-x").with(jwt()))
                .andExpect(status().isNotFound());

        verify(bubbleClient, never()).runWorkflow(anyString(), any());
    }

    @Test
    void addresses_requireJwt() throws Exception {
        mockMvc.perform(get("/api/consumer/addresses"))
                .andExpect(status().isUnauthorized());
    }
}
