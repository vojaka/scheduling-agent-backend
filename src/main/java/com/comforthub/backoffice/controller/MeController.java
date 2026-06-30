package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.model.entity.CompanyEntity;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.repository.CompanyRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.CurrentUserService.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Exposes the authenticated caller's company + role so the frontend can gate
 * its UI to match the backend's enforcement (e.g. hide OWNER-only actions like
 * Generate Shifts). Role is computed by the same {@link CurrentUserService} the
 * controllers enforce with, so the UI can never surface a right the backend
 * would refuse. Secured by the JWT resource server like everything under /api.
 */
@RestController
@RequestMapping("/api")
public class MeController {

    private final CurrentUserService currentUserService;
    private final BubbleUserRepository userRepository;
    private final CompanyRepository companyRepository;

    public MeController(CurrentUserService currentUserService,
                        BubbleUserRepository userRepository,
                        CompanyRepository companyRepository) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    @GetMapping("/me")
    public MeResponse me() {
        Role role = currentUserService.currentRole();
        String companyId = currentUserService.currentCompanyId().orElse(null);
        String companyName = currentUserService.currentCompanyName().orElse(null);
        return new MeResponse(companyId, companyName, role.name(), role == Role.OWNER);
    }

    @GetMapping("/me/companies")
    public ResponseEntity<List<CompanyEntity>> getAvailableCompanies() {
        Optional<BubbleUserEntity> userOpt = currentUserService.currentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        String bubbleId = userOpt.get().getBubbleId();
        if (bubbleId == null || bubbleId.isBlank()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(companyRepository.findAvailableCompanies(bubbleId));
    }

    @PostMapping("/me/company")
    public ResponseEntity<MeResponse> selectCompany(@RequestBody SelectCompanyRequest request) {
        if (request == null || request.companyId() == null || request.companyId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Optional<BubbleUserEntity> userOpt = currentUserService.currentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        BubbleUserEntity user = userOpt.get();
        String bubbleId = user.getBubbleId();
        if (bubbleId == null || bubbleId.isBlank()) {
            return ResponseEntity.status(403).build();
        }

        Optional<CompanyEntity> companyOpt = companyRepository.findById(request.companyId());
        if (companyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CompanyEntity company = companyOpt.get();

        // Check if the user is in owners or workers array
        boolean isOwner = arrayContains(company.getOwners(), bubbleId);
        boolean isWorker = arrayContains(company.getWorkers(), bubbleId);

        if (!isOwner && !isWorker) {
            return ResponseEntity.status(403).build();
        }

        // Update user entity
        user.setCompanyId(company.getId());
        user.setRole(isOwner ? "Merchant" : "Worker");
        userRepository.save(user);

        // Return updated me info
        String roleName = isOwner ? "OWNER" : "WORKER";
        return ResponseEntity.ok(new MeResponse(company.getId(), company.getName(), roleName, isOwner));
    }

    private static boolean arrayContains(String[] arr, String value) {
        if (arr == null || value == null) {
            return false;
        }
        for (String s : arr) {
            if (value.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** Identity + role for the current principal. `role` is OWNER | WORKER | NONE. */
    public record MeResponse(String companyId, String companyName, String role, boolean owner) {}

    /** Request payload to switch represented company */
    public record SelectCompanyRequest(String companyId) {}
}
