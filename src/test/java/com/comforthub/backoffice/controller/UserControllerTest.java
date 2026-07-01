package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.exception.ForbiddenException;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.WorkerInvitationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for the OWNER-only worker-management endpoints. Mirrors
 * {@code CompanyControllerTest} / {@code ScheduleControllerSecurityTest}: the
 * security + {@code GlobalExceptionHandler} layers are real (owner gate → 403),
 * while collaborators are mocked so the assertions cover the controller wiring,
 * company scoping, role mapping, HTTP status mapping and the #114 Bubble
 * write-through (invite creates the Bubble user; the mirror keeps its id).
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final String COMPANY = "company-123";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleUserRepository userRepository;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private WorkerInvitationService invitationService;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private JwtDecoder jwtDecoder;

    // ----------------------------------------------------------------- invite

    @Test
    void invite_asOwner_createsPendingWorker() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findByCompanyIdAndEmailIgnoreCase(COMPANY, "jane@example.com"))
                .thenReturn(Collections.emptyList());
        when(invitationService.invite(eq("jane@example.com"), any(), eq(COMPANY)))
                .thenReturn(Optional.of("auth0|new"));
        when(userRepository.save(any(BubbleUserEntity.class))).thenAnswer(inv -> {
            BubbleUserEntity e = inv.getArgument(0);
            e.setId(USER_ID);
            return e;
        });

        mockMvc.perform(post("/api/users/invite")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"name\":\"Jane\",\"role\":\"Worker\",\"maxHours\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.name").value("Jane"))
                .andExpect(jsonPath("$.role").value("Worker"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.maxHours").value(20));

        verify(currentUserService).requireOwner();
    }

    @Test
    @SuppressWarnings("unchecked")
    void invite_writesThroughToBubble_andKeepsBubbleIdOnMirrorRow() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findByCompanyIdAndEmailIgnoreCase(any(), any())).thenReturn(Collections.emptyList());
        when(invitationService.invite(any(), any(), any())).thenReturn(Optional.empty());
        when(bubbleClient.create(eq("user"), any())).thenReturn("bubble-user-1");
        when(userRepository.save(any(BubbleUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/users/invite")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"name\":\"Jane\",\"role\":\"Worker\",\"maxHours\":20}"))
                .andExpect(status().isCreated());

        // The Bubble create body uses the keys the ETL reads back (sync.py /
        // BubbleUser aliases / comforthub_schema.md § User).
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bubbleClient).create(eq("user"), bodyCaptor.capture());
        Map<String, Object> body = (Map<String, Object>) bodyCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("jane@example.com", body.get("email"));
        org.junit.jupiter.api.Assertions.assertEquals("Jane", body.get("name"));
        org.junit.jupiter.api.Assertions.assertEquals("Worker", body.get("role"));
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, body.get("active"));
        org.junit.jupiter.api.Assertions.assertEquals(COMPANY, body.get("Representing a Company"));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new BigDecimal("20").compareTo((BigDecimal) body.get("maxHours")));

        // The mirror row stores the Bubble id — the hourly ETL upsert (keyed on
        // bubble_id) lands on this same row, so no duplicate is ever created.
        ArgumentCaptor<BubbleUserEntity> savedCaptor = ArgumentCaptor.forClass(BubbleUserEntity.class);
        verify(userRepository).save(savedCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("bubble-user-1", savedCaptor.getValue().getBubbleId());
    }

    @Test
    void invite_bubbleCreateFails_membershipStillCreated() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findByCompanyIdAndEmailIgnoreCase(any(), any())).thenReturn(Collections.emptyList());
        when(invitationService.invite(any(), any(), any())).thenReturn(Optional.empty());
        when(bubbleClient.create(eq("user"), any())).thenThrow(new RuntimeException("bubble down"));
        when(userRepository.save(any(BubbleUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/users/invite")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"name\":\"Jane\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<BubbleUserEntity> savedCaptor = ArgumentCaptor.forClass(BubbleUserEntity.class);
        verify(userRepository).save(savedCaptor.capture());
        org.junit.jupiter.api.Assertions.assertNull(savedCaptor.getValue().getBubbleId());
    }

    @Test
    void invite_ownerRole_isStoredAsMerchant() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findByCompanyIdAndEmailIgnoreCase(any(), any())).thenReturn(Collections.emptyList());
        when(invitationService.invite(any(), any(), any())).thenReturn(Optional.empty());
        when(userRepository.save(any(BubbleUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/users/invite")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"boss@example.com\",\"name\":\"Boss\",\"role\":\"Owner\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("Owner"));

        ArgumentCaptor<BubbleUserEntity> captor = ArgumentCaptor.forClass(BubbleUserEntity.class);
        verify(userRepository).save(captor.capture());
        // UI "Owner" is stored as the OWNER-equivalent role the rest of the app uses.
        org.junit.jupiter.api.Assertions.assertEquals("Merchant", captor.getValue().getRole());
        org.junit.jupiter.api.Assertions.assertEquals(COMPANY, captor.getValue().getCompanyId());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, captor.getValue().getIsActive());
    }

    @Test
    void invite_asWorker_isForbidden() throws Exception {
        doThrow(new ForbiddenException("This action requires the OWNER role."))
                .when(currentUserService).requireOwner();

        mockMvc.perform(post("/api/users/invite")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(userRepository, never()).save(any());
        verify(bubbleClient, never()).create(any(), any());
    }

    @Test
    void invite_duplicateEmail_isConflict() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findByCompanyIdAndEmailIgnoreCase(COMPANY, "dupe@example.com"))
                .thenReturn(List.of(new BubbleUserEntity()));

        mockMvc.perform(post("/api/users/invite")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dupe@example.com\",\"name\":\"Dupe\"}"))
                .andExpect(status().isConflict());

        verify(userRepository, never()).save(any());
        verify(bubbleClient, never()).create(any(), any());
    }

    @Test
    void invite_invalidEmail_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/invite")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"name\":\"Nope\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    // ----------------------------------------------------------------- update

    @Test
    void update_asOwner_updatesFields() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingWorker()));
        when(userRepository.save(any(BubbleUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/users/" + USER_ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\",\"role\":\"Owner\",\"maxHours\":35,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.role").value("Owner"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.maxHours").value(35))
                // email is not editable — the original value survives.
                .andExpect(jsonPath("$.email").value("old@example.com"));

        verify(currentUserService).requireOwner();
    }

    @Test
    void update_withBubbleId_alsoPatchesBubbleRecord() throws Exception {
        BubbleUserEntity synced = existingWorker();
        synced.setBubbleId("bubble-user-1");
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(synced));
        when(userRepository.save(any(BubbleUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/users/" + USER_ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk());

        verify(bubbleClient).update(eq("user"), eq("bubble-user-1"), any());
    }

    @Test
    void update_withoutBubbleId_skipsBubblePatch() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingWorker()));
        when(userRepository.save(any(BubbleUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/users/" + USER_ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk());

        verify(bubbleClient, never()).update(any(), any(), any());
    }

    @Test
    void update_emailInBody_isIgnored() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existingWorker()));
        when(userRepository.save(any(BubbleUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/users/" + USER_ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"email\":\"hacker@evil.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("old@example.com"));
    }

    @Test
    void update_asWorker_isForbidden() throws Exception {
        doThrow(new ForbiddenException("This action requires the OWNER role."))
                .when(currentUserService).requireOwner();

        mockMvc.perform(put("/api/users/" + USER_ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void update_userInAnotherCompany_isNotFound() throws Exception {
        BubbleUserEntity other = existingWorker();
        other.setCompanyId("some-other-company");
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(other));

        mockMvc.perform(put("/api/users/" + USER_ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isNotFound());

        verify(userRepository, never()).save(any());
    }

    @Test
    void update_unknownUser_isNotFound() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(COMPANY));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/users/" + USER_ID)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isNotFound());
    }

    private static BubbleUserEntity existingWorker() {
        BubbleUserEntity e = new BubbleUserEntity();
        e.setId(USER_ID);
        e.setCompanyId(COMPANY);
        e.setFullName("Old Name");
        e.setRole("Worker");
        e.setEmail("old@example.com");
        e.setIsActive(true);
        e.setMaxHours(new BigDecimal("20"));
        return e;
    }
}
