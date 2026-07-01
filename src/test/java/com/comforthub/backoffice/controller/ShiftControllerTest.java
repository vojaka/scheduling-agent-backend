package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.dto.ShiftWriteRequest;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.ShiftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShiftController.class)
@Import(SecurityConfig.class)
class ShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShiftService shiftService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void createShift_success_returnsCreated() throws Exception {
        BubbleShiftEntity created = new BubbleShiftEntity();
        created.setId(UUID.randomUUID());
        created.setAssignedUser("user-1");
        created.setStartTime(OffsetDateTime.parse("2026-07-01T08:00:00Z"));
        created.setEndTime(OffsetDateTime.parse("2026-07-01T16:00:00Z"));
        created.setAssignedCompany("company-1");

        when(shiftService.create(any(ShiftWriteRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/shifts").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUser\":\"user-1\",\"startTime\":\"2026-07-01T08:00:00Z\",\"endTime\":\"2026-07-01T16:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.assignedUser").value("user-1"))
                .andExpect(jsonPath("$.assignedCompany").value("company-1"));
    }

    @Test
    void createShift_validationFailure_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/shifts").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        // missing assignedUser, startTime, endTime
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShift_invalidTimes_returnsBadRequest() throws Exception {
        when(shiftService.create(any(ShiftWriteRequest.class)))
                .thenThrow(new IllegalArgumentException("endTime must be after startTime"));

        mockMvc.perform(post("/api/shifts").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUser\":\"user-1\",\"startTime\":\"2026-07-01T16:00:00Z\",\"endTime\":\"2026-07-01T08:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("endTime must be after startTime"));
    }

    @Test
    void updateShift_success_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        BubbleShiftEntity updated = new BubbleShiftEntity();
        updated.setId(id);
        updated.setAssignedUser("user-1");
        updated.setStartTime(OffsetDateTime.parse("2026-07-01T09:00:00Z"));
        updated.setEndTime(OffsetDateTime.parse("2026-07-01T17:00:00Z"));
        updated.setAssignedCompany("company-1");

        when(shiftService.update(eq(id), any(ShiftWriteRequest.class))).thenReturn(Optional.of(updated));

        mockMvc.perform(put("/api/shifts/" + id).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUser\":\"user-1\",\"startTime\":\"2026-07-01T09:00:00Z\",\"endTime\":\"2026-07-01T17:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.startTime").value("2026-07-01T09:00:00Z"))
                .andExpect(jsonPath("$.endTime").value("2026-07-01T17:00:00Z"));
    }

    @Test
    void updateShift_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(shiftService.update(eq(id), any(ShiftWriteRequest.class))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/shifts/" + id).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUser\":\"user-1\",\"startTime\":\"2026-07-01T09:00:00Z\",\"endTime\":\"2026-07-01T17:00:00Z\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteShift_success_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        when(shiftService.delete(id)).thenReturn(true);

        mockMvc.perform(delete("/api/shifts/" + id).with(jwt()))
                .andExpect(status().isNoContent());

        verify(shiftService).delete(id);
    }

    @Test
    void deleteShift_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(shiftService.delete(id)).thenReturn(false);

        mockMvc.perform(delete("/api/shifts/" + id).with(jwt()))
                .andExpect(status().isNotFound());
    }
}
