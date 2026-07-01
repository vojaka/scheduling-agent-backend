package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.model.entity.CompanyEntity;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.repository.CompanyRepository;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.CurrentUserService.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private BubbleUserRepository userRepository;

    @MockBean
    private CompanyRepository companyRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void getMe_returnsCurrentUserInfo() throws Exception {
        when(currentUserService.currentRole()).thenReturn(Role.OWNER);
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        when(currentUserService.currentCompanyName()).thenReturn(Optional.of("Company One"));

        mockMvc.perform(get("/api/me")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value("c1"))
                .andExpect(jsonPath("$.companyName").value("Company One"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.owner").value(true));
    }

    @Test
    void getAvailableCompanies_returnsList() throws Exception {
        BubbleUserEntity user = new BubbleUserEntity();
        user.setBubbleId("b1");
        when(currentUserService.currentUser()).thenReturn(Optional.of(user));

        CompanyEntity company = new CompanyEntity("c1", "Company One", "REG1", new String[]{"b1"}, new String[]{});
        when(companyRepository.findAvailableCompanies("b1")).thenReturn(Collections.singletonList(company));

        mockMvc.perform(get("/api/me/companies")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("c1"))
                .andExpect(jsonPath("$[0].name").value("Company One"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void selectCompany_unauthorizedIfNoUser() throws Exception {
        when(currentUserService.currentUser()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/me/company")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"c1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void selectCompany_notFoundIfCompanyDoesNotExist() throws Exception {
        BubbleUserEntity user = new BubbleUserEntity();
        user.setBubbleId("b1");
        when(currentUserService.currentUser()).thenReturn(Optional.of(user));
        when(companyRepository.findById("c1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/me/company")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"c1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void selectCompany_forbiddenIfUserNotInCompany() throws Exception {
        BubbleUserEntity user = new BubbleUserEntity();
        user.setBubbleId("b1");
        when(currentUserService.currentUser()).thenReturn(Optional.of(user));

        CompanyEntity company = new CompanyEntity("c1", "Company One", "REG1", new String[]{"other"}, new String[]{});
        when(companyRepository.findById("c1")).thenReturn(Optional.of(company));

        mockMvc.perform(post("/api/me/company")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"c1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void selectCompany_successAsOwner() throws Exception {
        BubbleUserEntity user = new BubbleUserEntity();
        user.setBubbleId("b1");
        user.setCompanyId("old-co");
        user.setRole("Worker");
        when(currentUserService.currentUser()).thenReturn(Optional.of(user));

        CompanyEntity company = new CompanyEntity("c1", "Company One", "REG1", new String[]{"b1"}, new String[]{});
        when(companyRepository.findById("c1")).thenReturn(Optional.of(company));

        mockMvc.perform(post("/api/me/company")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"c1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value("c1"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.owner").value(true));

        verify(userRepository).save(user);
        org.junit.jupiter.api.Assertions.assertEquals("c1", user.getCompanyId());
        org.junit.jupiter.api.Assertions.assertEquals("Merchant", user.getRole());
    }

    @Test
    void selectCompany_successAsWorker() throws Exception {
        BubbleUserEntity user = new BubbleUserEntity();
        user.setBubbleId("b1");
        user.setCompanyId("old-co");
        user.setRole("Merchant");
        when(currentUserService.currentUser()).thenReturn(Optional.of(user));

        CompanyEntity company = new CompanyEntity("c1", "Company One", "REG1", new String[]{}, new String[]{"b1"});
        when(companyRepository.findById("c1")).thenReturn(Optional.of(company));

        mockMvc.perform(post("/api/me/company")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\":\"c1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value("c1"))
                .andExpect(jsonPath("$.role").value("WORKER"))
                .andExpect(jsonPath("$.owner").value(false));

        verify(userRepository).save(user);
        org.junit.jupiter.api.Assertions.assertEquals("c1", user.getCompanyId());
        org.junit.jupiter.api.Assertions.assertEquals("Worker", user.getRole());
    }

    @Test
    void corsAllowsPatchMethod() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/me")
                        .header("Origin", "https://backoffice-dev.comforthub.ee")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")));
    }
}
