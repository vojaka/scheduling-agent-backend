package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
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

import java.util.LinkedHashMap;
import java.util.Map;
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
 * <p><b>#114 Bubble write-through:</b> Bubble is the source of truth, so an
 * invite also CREATEs the user in Bubble (type {@code user}) and keeps the
 * returned Bubble id on the mirror row's {@code bubble_id}. The hourly ETL
 * upserts by {@code bubble_id}, so the same row is updated (never duplicated)
 * once the sync sees the Bubble record. If the Bubble create fails, the id
 * stays null and the pending membership is still created (best-effort, same
 * convention as the Auth0 dispatch); the failure is logged loudly for manual
 * reconciliation. Wiring the invitee's eventual Auth0 {@code sub} onto their
 * row after signup remains out-of-band (see V2 migration notes).
 *
 * <p><b>#117 (invite-500 fix, live 2026-07-02):</b> the Bubble create is the
 * AUTHORITATIVE step. The PostgreSQL mirror {@code save()} is best-effort: the
 * {@code users} table is owned by the Python ETL, so an insert can trip a
 * constraint the entity does not satisfy. Once Bubble has accepted the invite,
 * a mirror-save failure must NOT surface as a 500 — it is logged loudly and the
 * response is built from the in-memory entity; the hourly ETL reconciles.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    /** Bubble Data API object type for users (the ETL {@code /user} endpoint). */
    private static final String BUBBLE_USER_TYPE = "user";

    // ===== Bubble user field keys — chosen so the ETL reads them back =====
    // sync.py#sync_users reads "name", "role", "maxHours" and "active" as its
    // first-choice aliases (see also BubbleUser's @JsonAlias sets). "Representing
    // a Company" is the user type's company field per comforthub_schema.md § User
    // and is in BubbleUser's alias set. "email" is Bubble's built-in auth email —
    // the same key the bookings customer hop reads (BookingBubbleMapper).
    private static final String F_USER_EMAIL = "email";
    private static final String F_USER_NAME = "name";
    private static final String F_USER_ROLE = "role";
    private static final String F_USER_MAX_HOURS = "maxHours";
    private static final String F_USER_ACTIVE = "active";
    private static final String F_USER_COMPANY = "Representing a Company";

    private final BubbleUserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final WorkerInvitationService invitationService;
    private final BubbleClient bubbleClient;

    public UserController(BubbleUserRepository userRepository,
                          CurrentUserService currentUserService,
                          WorkerInvitationService invitationService,
                          BubbleClient bubbleClient) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.invitationService = invitationService;
        this.bubbleClient = bubbleClient;
    }

    /**
     * Invite a new worker / owner into the caller's company. Creates a pending
     * membership, (best-effort) emails an Auth0 account-setup link, and
     * (best-effort) creates the matching Bubble {@code user} record — see the
     * class doc for the write-through/dedupe contract.
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

        // #114 write-through: Bubble is the source of truth — also create the user
        // there so the invite is visible in Bubble and the hourly ETL round-trips
        // it. Best-effort, same convention as the Auth0 dispatch above. This is the
        // AUTHORITATIVE step: if it succeeds, the invite is a success regardless of
        // the mirror.
        try {
            String bubbleId = bubbleClient.create(BUBBLE_USER_TYPE, bubbleUserCreateBody(request, user, companyId));
            if (bubbleId != null && !bubbleId.isBlank()) {
                // Keyed on bubble_id, the hourly ETL upsert lands on this same
                // row — dedupe-safe, no duplicate membership is ever created.
                user.setBubbleId(bubbleId);
            }
        } catch (Exception e) {
            log.error("Bubble user create FAILED for {} (membership still created; the user "
                    + "will not exist in Bubble until reconciled): {}", email, e.getMessage());
        }

        // #117: the mirror save is best-effort. The users table is ETL-owned, so an
        // insert can trip a constraint the entity does not satisfy; once Bubble has
        // accepted the invite that must NOT 500 the request. On failure we log
        // loudly and answer from the in-memory entity (Bubble id + request data);
        // the hourly ETL reconciles the mirror row.
        BubbleUserEntity persisted = saveMirrorBestEffort(user, email);
        log.info("Invited {} to company {} as {} (bubbleId={})", email, companyId, persisted.getRole(), persisted.getBubbleId());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkerResponse.from(persisted));
    }

    /** #117: persist the mirror row; a failure is logged and the entity returned unsaved. */
    private BubbleUserEntity saveMirrorBestEffort(BubbleUserEntity user, String email) {
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            log.error("User mirror save FAILED for {} (bubbleId={}) — Bubble holds the authoritative "
                    + "record; the hourly ETL will reconcile the mirror: {}", email, user.getBubbleId(), e.getMessage());
            return user;
        }
    }

    /** POST /obj/user body — only the ETL-verified keys, only non-null values. */
    private static Map<String, Object> bubbleUserCreateBody(InviteWorkerRequest request,
                                                            BubbleUserEntity user,
                                                            String companyId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(F_USER_EMAIL, user.getEmail());
        if (request.name() != null && !request.name().isBlank()) {
            body.put(F_USER_NAME, request.name());
        }
        body.put(F_USER_ROLE, user.getRole());
        if (request.maxHours() != null) {
            body.put(F_USER_MAX_HOURS, request.maxHours());
        }
        body.put(F_USER_ACTIVE, false); // pending until the invitee signs up
        body.put(F_USER_COMPANY, companyId);
        return body;
    }

    /**
     * Partially update a worker in the caller's company. Email is not editable;
     * {@code active = false} soft-deactivates. 404 when the id does not resolve
     * to a user in the caller's company; 403 for non-owners.
     *
     * <p>#114 write-through: when the mirror row carries a {@code bubble_id}, the
     * same fields are PATCHed onto the Bubble {@code user} record (best-effort).
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
                    writeThroughUpdate(u, request);
                    BubbleUserEntity saved = userRepository.save(u);
                    return ResponseEntity.ok(WorkerResponse.from(saved));
                })
                .orElse(ResponseEntity.<WorkerResponse>notFound().build());
    }

    /** #114: PATCH the edited fields onto the Bubble user record — best-effort. */
    private void writeThroughUpdate(BubbleUserEntity user, UpdateWorkerRequest request) {
        if (user.getBubbleId() == null || user.getBubbleId().isBlank()) {
            // ETL has not linked this row to a Bubble record yet — nothing to patch.
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.name() != null) {
            body.put(F_USER_NAME, user.getFullName());
        }
        if (request.role() != null) {
            body.put(F_USER_ROLE, user.getRole());
        }
        if (request.maxHours() != null) {
            body.put(F_USER_MAX_HOURS, user.getMaxHours());
        }
        if (request.active() != null) {
            body.put(F_USER_ACTIVE, user.getIsActive());
        }
        if (body.isEmpty()) {
            return;
        }
        try {
            bubbleClient.update(BUBBLE_USER_TYPE, user.getBubbleId(), body);
        } catch (Exception e) {
            log.error("Bubble user update FAILED for {}/{} (mirror updated; Bubble is now stale "
                    + "until the next reconcile): {}", user.getBubbleId(), user.getEmail(), e.getMessage());
        }
    }
}
