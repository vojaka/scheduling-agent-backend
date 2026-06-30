package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.service.ShiftService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ShiftController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
class ShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShiftService shiftService;

    @Test
    void createReturns201() throws Exception {
        BubbleShiftEntity entity = new BubbleShiftEntity(
                "local-1", "u1",
                OffsetDateTime.of(2026, 6, 29, 8, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 6, 29, 16, 0, 0, 0, ZoneOffset.UTC),
                "Morning", "company-1", "Regular", "Approved", "store-1");
        Mockito.when(shiftService.create(any())).thenReturn(entity);

        mockMvc.perform(post("/api/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUser\":\"u1\",\"startTime\":\"2026-06-29T08:00:00Z\",\"endTime\":\"2026-06-29T16:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("local-1"))
                .andExpect(jsonPath("$.assignedUser").value("u1"));
    }

    @Test
    void createWithMissingFieldsReturns400() throws Exception {
        // Missing assignedUser/startTime/endTime -> @Valid @NotBlank fails before the service is called.
        mockMvc.perform(post("/api/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"oops\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithInvalidTimeRangeReturns400() throws Exception {
        Mockito.when(shiftService.create(any()))
                .thenThrow(new IllegalArgumentException("endTime must be after startTime"));

        mockMvc.perform(post("/api/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUser\":\"u1\",\"startTime\":\"2026-06-29T16:00:00Z\",\"endTime\":\"2026-06-29T08:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("endTime must be after startTime"));
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        Mockito.when(shiftService.delete(eq("nope"))).thenReturn(false);

        mockMvc.perform(delete("/api/shifts/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteExistingReturns204() throws Exception {
        Mockito.when(shiftService.delete(eq("local-1"))).thenReturn(true);

        mockMvc.perform(delete("/api/shifts/local-1"))
                .andExpect(status().isNoContent());
    }
}
