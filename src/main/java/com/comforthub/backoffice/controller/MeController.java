package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.CurrentUserService.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public MeController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public MeResponse me() {
        Role role = currentUserService.currentRole();
        String companyId = currentUserService.currentCompanyId().orElse(null);
        return new MeResponse(companyId, role.name(), role == Role.OWNER);
    }

    /** Identity + role for the current principal. `role` is OWNER | WORKER | NONE. */
    public record MeResponse(String companyId, String role, boolean owner) {}
}
