package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.dto.ScheduleGenerateResponse;
import com.comforthub.backoffice.exception.ForbiddenException;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.BubbleSyncService;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.ScheduleOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the OWNER-only gate on schedule generate/commit end-to-end through
 * the HTTP + security + {@code GlobalExceptionHandler} layers.
 *
 * <ul>
 *   <li>Non-owner (WORKER or unresolved NONE) → {@code requireOwner()} throws
 *       {@link ForbiddenException} → <b>403</b>.</li>
 *   <li>OWNER → gate passes → <b>200</b>.</li>
 *   <li>No token → <b>401</b> (security still active).</li>
 * </ul>
 *
 * The role-resolution logic itself (token → OWNER/WORKER/NONE, incl. the
 * fail-safe) is covered by {@code CurrentUserServiceTest}; here it is mocked so
 * the test asserts only the controller wiring + HTTP status mapping.
 */
@WebMvcTest(ScheduleController.class)
@Import(SecurityConfig.class)
class ScheduleControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleOrchestrationService orchestrationService;

    @MockBean
    private BubbleClient bubbleClient;

    @MockBean
    private BubbleSyncService bubbleSyncService;

    @MockBean
    private CurrentUserService currentUserService;

    // Replaces the real decoder so SecurityConfig never calls Auth0 at startup.
    @MockBean
    private JwtDecoder jwtDecoder;

    // --- generate ---

    @Test
    void worker_generate_isForbidden() throws Exception {
        doThrow(new ForbiddenException("This action requires the OWNER role."))
                .when(currentUserService).requireOwner();

        mockMvc.perform(post("/api/schedule/generate")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"make a schedule\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void owner_generate_isOk() throws Exception {
        // requireOwner() is a void mock → does nothing → owner allowed.
        when(orchestrationService.generateSchedule(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ScheduleGenerateResponse());

        mockMvc.perform(post("/api/schedule/generate")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"make a schedule\"}"))
                .andExpect(status().isOk());
    }

    // --- commit ---

    @Test
    void worker_commit_isForbidden() throws Exception {
        doThrow(new ForbiddenException("This action requires the OWNER role."))
                .when(currentUserService).requireOwner();

        mockMvc.perform(post("/api/schedule/commit")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void owner_commit_isOk() throws Exception {
        mockMvc.perform(post("/api/schedule/commit")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insertedCount").value(0));
    }

    // --- security still active ---

    @Test
    void unauthenticated_generate_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/schedule/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"make a schedule\"}"))
                .andExpect(status().isUnauthorized());
    }
}
