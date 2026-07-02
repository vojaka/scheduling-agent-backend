package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.dto.ScheduleGenerateRequest;
import com.comforthub.backoffice.dto.ScheduleGenerateResponse;
import com.comforthub.backoffice.exception.GeminiUnavailableException;
import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.dto.ShiftResponseDto;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.service.BubbleSyncService;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.ScheduleOrchestrationService;
import com.comforthub.backoffice.service.ShiftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);

    /**
     * #115: Metabase dashboards the backoffice is allowed to embed. The Analytics
     * page uses dashboard 2 ({@code AnalyticsPage.tsx} —
     * {@code GET /dashboard-url?dashboardId=2}); no other dashboard id is
     * referenced by the frontend or docs. Anything else is rejected with 400
     * instead of being signed blindly into an embed token.
     */
    private static final Set<Integer> ALLOWED_DASHBOARD_IDS = Set.of(2);

    private final ScheduleOrchestrationService orchestrationService;
    private final BubbleClient bubbleClient;
    private final BubbleSyncService bubbleSyncService;
    private final CurrentUserService currentUserService;
    private final ShiftService shiftService;

    @Value("${metabase.site.url:http://178.105.76.235:3000}")
    private String metabaseSiteUrl;

    @Value("${metabase.embed.secret:}")
    private String metabaseEmbedSecret;

    public ScheduleController(ScheduleOrchestrationService orchestrationService,
                              BubbleClient bubbleClient,
                              BubbleSyncService bubbleSyncService,
                              CurrentUserService currentUserService,
                              ShiftService shiftService) {
        this.orchestrationService = orchestrationService;
        this.bubbleClient = bubbleClient;
        this.bubbleSyncService = bubbleSyncService;
        this.currentUserService = currentUserService;
        this.shiftService = shiftService;
    }

    @GetMapping("/dashboard-url")
    public ResponseEntity<Map<String, Object>> getDashboardUrl(
            @RequestParam(name = "dashboardId") int dashboardId,
            @RequestParam(name = "company", required = false) String company,
            @RequestParam(name = "store", required = false) String store) {
        log.info("Generating Metabase embed URL for dashboard {}, company={}, store={}", dashboardId, company, store);

        if (!ALLOWED_DASHBOARD_IDS.contains(dashboardId)) {
            log.warn("Rejected dashboard-url request for non-allowlisted dashboard {}", dashboardId);
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "Unknown dashboardId: " + dashboardId));
        }

        if (metabaseEmbedSecret == null || metabaseEmbedSecret.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "METABASE_EMBED_SECRET is not configured."));
        }

        try {
            com.auth0.jwt.algorithms.Algorithm algorithm =
                    com.auth0.jwt.algorithms.Algorithm.HMAC256(metabaseEmbedSecret);

            Map<String, Object> resource = new HashMap<>();
            resource.put("dashboard", dashboardId);

            Map<String, Object> params = new HashMap<>();
            if (company != null && !company.trim().isEmpty()) params.put("company", company);
            if (store != null && !store.trim().isEmpty()) params.put("store", store);

            long expSeconds = (System.currentTimeMillis() / 1000L) + 600L;

            String token = com.auth0.jwt.JWT.create()
                    .withClaim("resource", resource)
                    .withClaim("params", params)
                    .withClaim("exp", expSeconds)
                    .sign(algorithm);

            String embedUrl = String.format("%s/embed/dashboard/%s#theme=light&bordered=false&titled=true",
                    metabaseSiteUrl, token);

            return ResponseEntity.ok(Map.of("status", "success", "url", embedUrl));
        } catch (Exception e) {
            log.error("Failed to generate Metabase embed URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        log.info("Manual Bubble → PostgreSQL sync triggered.");
        String result = bubbleSyncService.syncNow();
        Map<String, Object> response = new HashMap<>();
        response.put("status", result.contains("failed") ? "error" : "success");
        response.put("message", result);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate")
    public ResponseEntity<ScheduleGenerateResponse> generate(@RequestBody ScheduleGenerateRequest request) {
        // OWNER-only: workers may view shifts but cannot generate or commit schedules.
        currentUserService.requireOwner();
        log.info("Schedule generation request. Prompt: {}, auto-commit: {}", request.getPrompt(), request.getCommit());

        String prompt = request.getPrompt() != null ? request.getPrompt() : "";
        // Derive the company from the authenticated user; the frontend no longer sends it.
        // Fall back to any request-supplied company for non-interactive/service callers.
        String company = currentUserService.currentCompanyId().orElse(request.getCompany());
        ScheduleGenerateResponse response = orchestrationService.generateSchedule(
                prompt, request.getMonth(), company, request.getWorkers(),
                request.getBufferBeforeMinutes(), request.getBufferAfterMinutes());

        if (Boolean.TRUE.equals(request.getCommit())) {
            if (response.getValidationReport() != null && response.getValidationReport().isValid()
                    && response.getProposedShifts() != null) {
                log.info("Schedule valid. Auto-committing {} shifts to Bubble.", response.getProposedShifts().size());
                response.getOrchestratorLogs().add("Auto-committing proposed shifts to Bubble...");

                int successCount = 0;
                for (ShiftResponseDto shiftDto : response.getProposedShifts()) {
                    try {
                        BubbleShift bs = new BubbleShift();
                        bs.setAssignedUser(shiftDto.getAssignedUser());
                        bs.setStartTime(shiftDto.getStartTime());
                        bs.setEndTime(shiftDto.getEndTime());
                        bs.setNotes(shiftDto.getNotes());
                        bs.setAssignedCompany(shiftDto.getAssignedCompany());
                        bs.setType(shiftDto.getType());
                        bs.setAssignedStore(shiftDto.getAssignedStore());

                        BubbleShift created = bubbleClient.createShift(bs);
                        if (created != null && created.getId() != null) {
                            shiftDto.setId(created.getId());
                            successCount++;
                            persistMirror(shiftDto);
                        }
                    } catch (Exception e) {
                        log.error("Failed to commit shift for user {}: {}", shiftDto.getAssignedUser(), e.getMessage());
                    }
                }
                response.getOrchestratorLogs().add("Auto-committed " + successCount + " shifts to Bubble.");
            } else {
                log.warn("Auto-commit skipped: schedule invalid or empty.");
                response.getOrchestratorLogs().add("Auto-commit skipped: schedule invalid or empty.");
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/commit")
    public ResponseEntity<Map<String, Object>> commit(@RequestBody List<BubbleShift> shifts) {
        // OWNER-only: workers may view shifts but cannot generate or commit schedules.
        currentUserService.requireOwner();
        log.info("Bulk commit of {} shifts to Bubble.", shifts != null ? shifts.size() : 0);

        if (shifts == null || shifts.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "success", "insertedCount", 0, "message", "No shifts provided."));
        }

        List<String> createdIds = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (BubbleShift shift : shifts) {
            try {
                shift.setId(null);
                BubbleShift created = bubbleClient.createShift(shift);
                if (created != null && created.getId() != null) {
                    createdIds.add(created.getId());
                    successCount++;
                    persistMirror(new ShiftResponseDto(created));
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                log.error("Failed to commit shift for user {}: {}", shift.getAssignedUser(), e.getMessage());
                failureCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", failureCount == 0 ? "success" : "partial_success");
        result.put("insertedCount", successCount);
        result.put("failedCount", failureCount);
        result.put("createdIds", createdIds);
        return ResponseEntity.ok(result);
    }

    /**
     * #114: after a shift is committed to Bubble (the source of truth), upsert it
     * into the PostgreSQL mirror (keyed on {@code bubble_id}) so Metabase sees it
     * before the next hourly ETL run. Best-effort — Bubble already has the row.
     */
    private void persistMirror(ShiftResponseDto dto) {
        try {
            shiftService.persistGenerated(dto);
        } catch (Exception e) {
            log.error("Failed to mirror committed shift {} into PostgreSQL (ETL will catch up): {}",
                    dto.getId(), e.getMessage());
        }
    }

    @ExceptionHandler(GeminiUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleGeminiUnavailable(GeminiUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Service Unavailable", "message", ex.getMessage()));
    }
}
