package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.CompanyEntity;
import com.comforthub.backoffice.repository.CompanyRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;

    public CompanyController(CompanyRepository companyRepository,
                             CurrentUserService currentUserService) {
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<CompanyEntity>> getCompanies() {
        Optional<String> companyIdOpt = currentUserService.currentCompanyId();
        if (companyIdOpt.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return companyRepository.findById(companyIdOpt.get())
                .map(Collections::singletonList)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Collections.emptyList()));
    }
}
