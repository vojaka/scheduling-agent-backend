package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.dto.ReportDto;
import com.comforthub.backoffice.security.SecurityConfig;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.MetabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetabaseService metabaseService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void getReports_returnsReportsList() throws Exception {
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-123"));
        when(metabaseService.getReports("company-123")).thenReturn(List.of(
                new ReportDto("card-1", "Hours Report", "Worker Hours", "question", "http://embed-url-1"),
                new ReportDto("dashboard-2", "Sales Dashboard", "Revenue details", "dashboard", "http://embed-url-2")
        ));

        mockMvc.perform(get("/api/reports")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("card-1"))
                .andExpect(jsonPath("$[0].name").value("Hours Report"))
                .andExpect(jsonPath("$[0].type").value("question"))
                .andExpect(jsonPath("$[0].embedUrl").value("http://embed-url-1"))
                .andExpect(jsonPath("$[1].id").value("dashboard-2"))
                .andExpect(jsonPath("$[1].name").value("Sales Dashboard"))
                .andExpect(jsonPath("$[1].type").value("dashboard"))
                .andExpect(jsonPath("$[1].embedUrl").value("http://embed-url-2"));
    }
}
