package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the company-scoped worker list, which shares the compact
 * {@link com.comforthub.backoffice.dto.WorkerResponse} shape with the invite /
 * edit endpoints: title-case role, {@code name}/{@code active} field names,
 * {@code bubbleId} exposed (needed by the Company page to resolve owner/worker
 * names), and {@code auth0UserId}/{@code companyId} kept internal-only.
 */
@WebMvcTest(DataController.class)
@Import(SecurityConfig.class)
class DataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BubbleShiftRepository shiftRepository;

    @MockBean
    private BubbleUserRepository userRepository;

    @MockBean
    private BubbleStoreRepository storeRepository;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void getUsers_returnsCompactWorkerShape_withTitleCaseRole() throws Exception {
        BubbleUserEntity owner = new BubbleUserEntity();
        owner.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        owner.setBubbleId("bubble-1");
        owner.setAuth0UserId("auth0|secret");
        owner.setCompanyId("company-123");
        owner.setFullName("Ada Owner");
        owner.setRole("Merchant"); // stored owner role -> "Owner" on the wire
        owner.setEmail("ada@example.com");
        owner.setIsActive(true);
        owner.setMaxHours(new BigDecimal("40"));

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-123"));
        when(userRepository.findByCompanyId(eq("company-123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(owner)));

        mockMvc.perform(get("/api/users").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("22222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.content[0].bubbleId").value("bubble-1"))
                .andExpect(jsonPath("$.content[0].name").value("Ada Owner"))
                .andExpect(jsonPath("$.content[0].role").value("Owner"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].email").value("ada@example.com"))
                // auth0/company scoping columns stay internal-only.
                .andExpect(jsonPath("$.content[0].auth0UserId").doesNotExist())
                .andExpect(jsonPath("$.content[0].companyId").doesNotExist());
    }

    @Test
    void getUsers_noResolvableCompany_returnsEmptyPage() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
