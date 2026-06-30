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

    @PutMapping("/{id}")
    public ResponseEntity<CompanyEntity> updateCompany(@PathVariable String id, @RequestBody CompanyEntity body) {
        currentUserService.requireOwner();
        Optional<String> companyIdOpt = currentUserService.currentCompanyId();
        if (companyIdOpt.isEmpty() || !companyIdOpt.get().equals(id)) {
            return ResponseEntity.status(403).build();
        }
        return companyRepository.findById(id)
                .map(existing -> {
                    if (body.getName() != null) {
                        existing.setName(body.getName());
                    }
                    if (body.getRegCode() != null) {
                        existing.setRegCode(body.getRegCode());
                    }
                    return ResponseEntity.ok(companyRepository.save(existing));
                })
                .orElse(ResponseEntity.<CompanyEntity>notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable String id) {
        currentUserService.requireOwner();
        Optional<String> companyIdOpt = currentUserService.currentCompanyId();
        if (companyIdOpt.isEmpty() || !companyIdOpt.get().equals(id)) {
            return ResponseEntity.status(403).build();
        }
        Optional<CompanyEntity> companyOpt = companyRepository.findById(id);
        Optional<ResponseEntity<Void>> responseOpt = companyOpt.map(existing -> {
            companyRepository.delete(existing);
            return ResponseEntity.<Void>ok().build();
        });
        return responseOpt.orElse(ResponseEntity.<Void>notFound().build());
    }
}
