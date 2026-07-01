package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.dto.InviteWorkerRequest;
import com.comforthub.backoffice.dto.RoleMapping;
import com.comforthub.backoffice.dto.UpdateWorkerRequest;
import com.comforthub.backoffice.dto.WorkerResponse;
import com.comforthub.backoffice.exception.ForbiddenException;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.WorkerInvitationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * OWNER-only worker management for the backoffice: invite a new worker / owner
 * and edit an existing one. Both are gated by {@link CurrentUserService#requireOwner()}
 * (403 for non-owners, via {@code GlobalExceptionHandler}) and scoped to the
 * caller's company — an owner can only touch users whose {@code company_id}
 * matches their own. Reads of the worker list stay in {@code DataController}
 * ({@code GET /api/users}).
 *
 * <p><b>Contract:</b> requests and responses use the UI's title-case role
 * vocabulary ("Worker" / "Owner"), mapped to/from the stored role strings by
 * {@link RoleMapping}. Responses use the compact {@link WorkerResponse} shape
 * {@code { id, name, role, maxHours, active, email }}.
 *
 * <p><b>Invited users</b> are persisted with {@code is_active = false} (pending)
 * and no {@code bubble_id}, so the hourly Bubble sync (which upserts by
 * {@code bubble_id}) never overwrites them. Wiring the invitee's eventual Auth0
 * {@code sub} onto their row after signup, and reconciling with any later Bubble
 * sync, is handled out-of-band (see V2 migration notes) and is outside this
 * endpoint's scope.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final BubbleUserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final WorkerInvitationService invitationService;

    public UserController(BubbleUserRepository userRepository,
                          CurrentUserService currentUserService,
                          WorkerInvitationService invitationService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.invitationService = invitationService;
    }

    /**
     * Invite a new worker / owner into the caller's company. Creates a pending
     * membership and (best-effort) emails an Auth0 account-setup link.
     *
     * <p>400 invalid email · 409 already a member of this company · 403 non-owner.
     */
    @PostMapping("/invite")
    public ResponseEntity<WorkerResponse> invite(@Valid @RequestBody InviteWorkerRequest request) {
        currentUserService.requireOwner();
        String companyId = currentUserService.currentCompanyId()
                .orElseThrow(() -> new ForbiddenException("No company context for the current user."));

        String email = request.email().trim();

        if (!userRepository.findByCompanyIdAndEmailIgnoreCase(companyId, email).isEmpty()) {
            log.info("Invite rejected — {} is already a member of company {}", email, companyId);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        // Best-effort: never let an invite-dispatch failure block the membership.
        Optional<String> auth0UserId;
        try {
            auth0UserId = invitationService.invite(email, request.name(), companyId);
        } catch (Exception e) {
            log.warn("Auth0 invite dispatch threw for {} (membership still created): {}", email, e.getMessage());
            auth0UserId = Optional.empty();
        }

        BubbleUserEntity user = new BubbleUserEntity();
        user.setEmail(email);
        user.setFullName(request.name());
        user.setRole(RoleMapping.toStored(request.role()));
        user.setMaxHours(request.maxHours());
        user.setIsActive(false); // pending until the invitee signs up
        user.setCompanyId(companyId);
        auth0UserId.ifPresent(user::setAuth0UserId);

        BubbleUserEntity saved = userRepository.save(user);
        log.info("Invited {} to company {} as {}", email, companyId, saved.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkerResponse.from(saved));
    }

    /**
     * Partially update a worker in the caller's company. Email is not editable;
     * {@code active = false} soft-deactivates. 404 when the id does not resolve
     * to a user in the caller's company; 403 for non-owners.
     */
    @PutMapping("/{id}")
    public ResponseEntity<WorkerResponse> update(@PathVariable String id,
                                                 @RequestBody UpdateWorkerRequest request) {
        currentUserService.requireOwner();
        Optional<String> companyIdOpt = currentUserService.currentCompanyId();
        if (companyIdOpt.isEmpty()) {
            return ResponseEntity.<WorkerResponse>notFound().build();
        }
        String companyId = companyIdOpt.get();

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.<WorkerResponse>notFound().build();
        }

        return userRepository.findById(uuid)
                .filter(u -> companyId.equals(u.getCompanyId()))
                .map(u -> {
                    if (request.name() != null) {
                        u.setFullName(request.name());
                    }
                    if (request.role() != null) {
                        u.setRole(RoleMapping.toStored(request.role()));
                    }
                    if (request.maxHours() != null) {
                        u.setMaxHours(request.maxHours());
                    }
                    if (request.active() != null) {
                        u.setIsActive(request.active());
                    }
                    // email is intentionally NOT editable.
                    BubbleUserEntity saved = userRepository.save(u);
                    return ResponseEntity.ok(WorkerResponse.from(saved));
                })
                .orElse(ResponseEntity.<WorkerResponse>notFound().build());
    }
}
