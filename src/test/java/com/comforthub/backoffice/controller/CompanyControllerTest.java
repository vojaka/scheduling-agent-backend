package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.exception.ForbiddenException;
import com.comforthub.backoffice.model.entity.CompanyEntity;
import com.comforthub.backoffice.repository.CompanyRepository;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyController.class)
@Import(SecurityConfig.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyRepository companyRepository;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void getCompanies_returnsCompanyList() throws Exception {
        CompanyEntity company = new CompanyEntity("company-123", "My Company", "REG-123", new String[]{"owner1"}, new String[]{"worker1"});
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-123"));
        when(companyRepository.findById("company-123")).thenReturn(Optional.of(company));

        mockMvc.perform(get("/api/companies")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("company-123"))
                .andExpect(jsonPath("$[0].name").value("My Company"))
                .andExpect(jsonPath("$[0].regCode").value("REG-123"));
    }

    @Test
    void updateCompany_asOwner_success() throws Exception {
        CompanyEntity existing = new CompanyEntity("company-123", "My Company", "REG-123", new String[]{"owner1"}, new String[]{"worker1"});
        CompanyEntity updated = new CompanyEntity("company-123", "Updated Company Name", "REG-999", new String[]{"owner1"}, new String[]{"worker1"});
        
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-123"));
        when(companyRepository.findById("company-123")).thenReturn(Optional.of(existing));
        when(companyRepository.save(any(CompanyEntity.class))).thenReturn(updated);

        mockMvc.perform(put("/api/companies/company-123")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Company Name\",\"regCode\":\"REG-999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Company Name"))
                .andExpect(jsonPath("$.regCode").value("REG-999"));

        verify(currentUserService).requireOwner();
    }

    @Test
    void updateCompany_asWorker_forbidden() throws Exception {
        doThrow(new ForbiddenException("This action requires the OWNER role."))
                .when(currentUserService).requireOwner();

        mockMvc.perform(put("/api/companies/company-123")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Company Name\",\"regCode\":\"REG-999\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCompany_wrongCompanyId_forbidden() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("other-company"));

        mockMvc.perform(put("/api/companies/company-123")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Company Name\",\"regCode\":\"REG-999\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCompany_asOwner_success() throws Exception {
        CompanyEntity existing = new CompanyEntity("company-123", "My Company", "REG-123", new String[]{"owner1"}, new String[]{"worker1"});
        
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-123"));
        when(companyRepository.findById("company-123")).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/companies/company-123")
                        .with(jwt()))
                .andExpect(status().isOk());

        verify(currentUserService).requireOwner();
        verify(companyRepository).delete(existing);
    }

    @Test
    void deleteCompany_asWorker_forbidden() throws Exception {
        doThrow(new ForbiddenException("This action requires the OWNER role."))
                .when(currentUserService).requireOwner();

        mockMvc.perform(delete("/api/companies/company-123")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }
}
