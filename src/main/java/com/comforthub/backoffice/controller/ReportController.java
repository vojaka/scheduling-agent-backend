package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.dto.ReportDto;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.MetabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final MetabaseService metabaseService;
    private final CurrentUserService currentUserService;

    public ReportController(MetabaseService metabaseService, CurrentUserService currentUserService) {
        this.metabaseService = metabaseService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<ReportDto>> getReports() {
        String companyId = currentUserService.currentCompanyId().orElse(null);
        log.info("Fetching Metabase reports for company ID: {}", companyId);
        List<ReportDto> reports = metabaseService.getReports(companyId);
        return ResponseEntity.ok(reports);
    }
}
