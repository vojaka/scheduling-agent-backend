package com.example.scheduler.controller;

import com.example.scheduler.client.BubbleClient;
import com.example.scheduler.dto.ScheduleGenerateRequest;
import com.example.scheduler.dto.ScheduleGenerateResponse;
import com.example.scheduler.model.BubbleShift;
import com.example.scheduler.service.OrchestrationService;
import com.example.scheduler.exception.GeminiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);

    private final OrchestrationService orchestrationService;
    private final BubbleClient bubbleClient;
    private final com.example.scheduler.service.SupabaseSyncService supabaseSyncService;

    @Value("${metabase.site.url:http://178.105.76.235:3000}")
    private String metabaseSiteUrl;

    @Value("${metabase.embed.secret:}")
    private String metabaseEmbedSecret;

    public ScheduleController(OrchestrationService orchestrationService, 
                              BubbleClient bubbleClient, 
                              com.example.scheduler.service.SupabaseSyncService supabaseSyncService) {
        this.orchestrationService = orchestrationService;
        this.bubbleClient = bubbleClient;
        this.supabaseSyncService = supabaseSyncService;
    }

    @GetMapping("/dashboard-url")
    public ResponseEntity<Map<String, Object>> getDashboardUrl(
            @RequestParam(name = "dashboardId") int dashboardId,
            @RequestParam(name = "company", required = false) String company,
            @RequestParam(name = "store", required = false) String store) {
        log.info("API request received to generate Metabase secure embed URL for dashboard ID: {}, company filter: {}, store filter: {}", 
                dashboardId, company, store);
        
        if (metabaseEmbedSecret == null || metabaseEmbedSecret.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "METABASE_EMBED_SECRET is not configured on the backend.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }

        try {
            com.auth0.jwt.algorithms.Algorithm algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(metabaseEmbedSecret);
            
            Map<String, Object> resource = new HashMap<>();
            resource.put("dashboard", dashboardId);

            Map<String, Object> params = new HashMap<>();
            if (company != null && !company.trim().isEmpty()) {
                params.put("company", company);
            }
            if (store != null && !store.trim().isEmpty()) {
                params.put("store", store);
            }

            long expSeconds = (System.currentTimeMillis() / 1000L) + 600L; // 10 minutes expiry

            String token = com.auth0.jwt.JWT.create()
                    .withClaim("resource", resource)
                    .withClaim("params", params)
                    .withClaim("exp", expSeconds)
                    .sign(algorithm);

            String embedUrl = String.format("%s/embed/dashboard/%s#theme=light&bordered=false&titled=true", 
                    metabaseSiteUrl, token);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("url", embedUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate Metabase secure embed URL", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        log.info("API request received for manual database sync to Supabase.");
        String result = supabaseSyncService.syncNow();
        Map<String, Object> response = new HashMap<>();
        response.put("status", result.contains("failed") ? "error" : "success");
        response.put("message", result);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/generate")
    public ResponseEntity<ScheduleGenerateResponse> generate(@RequestBody ScheduleGenerateRequest request) {
        log.info("API request received for schedule generation. Prompt: {}, auto-commit: {}", 
                request.getPrompt(), request.getCommit());
        
        String prompt = request.getPrompt() != null ? request.getPrompt() : "";
        ScheduleGenerateResponse response = orchestrationService.generateSchedule(
                prompt, request.getMonth(), request.getCompany(), request.getWorkers(),
                request.getBufferBeforeMinutes(), request.getBufferAfterMinutes());

        if (Boolean.TRUE.equals(request.getCommit())) {
            if (response.getValidationReport() != null && response.getValidationReport().isValid() && response.getProposedShifts() != null) {
                log.info("Schedule is valid. Automatically committing {} shifts back to Bubble.", response.getProposedShifts().size());
                response.getOrchestratorLogs().add("Schedule is valid. Automatically committing proposed shifts to Bubble...");
                
                int successCount = 0;
                for (com.example.scheduler.dto.ShiftResponseDto shiftDto : response.getProposedShifts()) {
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
                        }
                    } catch (Exception e) {
                        log.error("Failed to auto-commit shift to Bubble user {}: {}", shiftDto.getAssignedUser(), e.getMessage());
                    }
                }
                response.getOrchestratorLogs().add("Successfully automatically committed " + successCount + " shifts to Bubble.");
            } else {
                log.warn("Auto-commit skipped because proposed schedule was invalid or empty.");
                response.getOrchestratorLogs().add("Auto-commit skipped because proposed schedule was invalid or empty.");
            }
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/commit")
    public ResponseEntity<Map<String, Object>> commit(@RequestBody List<BubbleShift> shifts) {
        log.info("API request received for bulk committing {} shifts back to Bubble.", 
                shifts != null ? shifts.size() : 0);

        Map<String, Object> result = new HashMap<>();
        
        if (shifts == null || shifts.isEmpty()) {
            result.put("status", "success");
            result.put("insertedCount", 0);
            result.put("message", "No shifts provided to commit.");
            return ResponseEntity.ok(result);
        }

        List<String> createdIds = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (BubbleShift shift : shifts) {
            try {
                // Clear any pre-existing ID from draft state before inserting into Bubble
                shift.setId(null);
                
                BubbleShift created = bubbleClient.createShift(shift);
                if (created != null && created.getId() != null) {
                    createdIds.add(created.getId());
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                log.error("Failed to commit shift to Bubble user {}: {}", shift.getAssignedUser(), e.getMessage());
                failureCount++;
            }
        }

        result.put("status", failureCount == 0 ? "success" : "partial_success");
        result.put("insertedCount", successCount);
        result.put("failedCount", failureCount);
        result.put("createdIds", createdIds);

        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(GeminiUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleGeminiUnavailable(GeminiUnavailableException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Service Unavailable");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
}
