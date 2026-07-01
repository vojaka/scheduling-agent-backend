package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.CompanyCredentialEntity;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CompanyCredentialService;
import com.comforthub.backoffice.service.CredentialEncryptionService;
import com.comforthub.backoffice.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyCredentialController.class)
@Import(SecurityConfig.class)
class CompanyCredentialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyCredentialService credentialService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private CredentialEncryptionService encryptionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/companies/credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_withoutCompany_isForbidden() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/companies/credentials").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCredentials_returnsMaskedValues() throws Exception {
        String companyId = "c1";
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of(companyId));

        CompanyCredentialEntity entity = new CompanyCredentialEntity();
        entity.setId(UUID.randomUUID());
        entity.setProvider("MONTONIO");
        entity.setKeyName("secret_key");
        entity.setEncryptedValue("enc-val");
        entity.setNonce("nonce-val");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        when(credentialService.getCredentialsForCompany(companyId))
                .thenReturn(Collections.singletonList(entity));
        when(encryptionService.decrypt("enc-val", "nonce-val"))
                .thenReturn("my-super-secret-key-12345");

        mockMvc.perform(get("/api/companies/credentials").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("MONTONIO"))
                .andExpect(jsonPath("$[0].keyName").value("secret_key"))
                // Expect masked value (first 2 and last 2 chars visible, e.g. "my***45")
                .andExpect(jsonPath("$[0].maskedValue").value("my***45"));
    }

    @Test
    void saveCredential_asOwner_succeeds() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        doNothing().when(currentUserService).requireOwner();

        mockMvc.perform(post("/api/companies/credentials")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MONTONIO\",\"keyName\":\"access_key\",\"value\":\"my-access-key\"}"))
                .andExpect(status().isOk());

        verify(credentialService).saveCredential("c1", "MONTONIO", "access_key", "my-access-key");
    }

    @Test
    void saveCredential_asWorker_isForbidden() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        doThrow(new com.comforthub.backoffice.exception.ForbiddenException("Requires OWNER"))
                .when(currentUserService).requireOwner();

        mockMvc.perform(post("/api/companies/credentials")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MONTONIO\",\"keyName\":\"access_key\",\"value\":\"my-access-key\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCredential_asOwner_succeeds() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        doNothing().when(currentUserService).requireOwner();

        mockMvc.perform(delete("/api/companies/credentials")
                        .with(jwt())
                        .param("provider", "MONTONIO")
                        .param("keyName", "access_key"))
                .andExpect(status().isNoContent());

        verify(credentialService).deleteCredential("c1", "MONTONIO", "access_key");
    }

    @Test
    void deleteCredential_asWorker_isForbidden() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("c1"));
        doThrow(new com.comforthub.backoffice.exception.ForbiddenException("Requires OWNER"))
                .when(currentUserService).requireOwner();

        mockMvc.perform(delete("/api/companies/credentials")
                        .with(jwt())
                        .param("provider", "MONTONIO")
                        .param("keyName", "access_key"))
                .andExpect(status().isForbidden());
    }
}
