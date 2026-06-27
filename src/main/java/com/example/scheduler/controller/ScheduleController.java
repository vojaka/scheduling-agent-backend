package com.example.scheduler.controller;

import com.example.scheduler.client.BubbleClient;
import com.example.scheduler.dto.ScheduleGenerateRequest;
import com.example.scheduler.dto.ScheduleGenerateResponse;
import com.example.scheduler.model.BubbleShift;
import com.example.scheduler.service.OrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public ScheduleController(OrchestrationService orchestrationService, BubbleClient bubbleClient) {
        this.orchestrationService = orchestrationService;
        this.bubbleClient = bubbleClient;
    }

    @PostMapping("/generate")
    public ResponseEntity<ScheduleGenerateResponse> generate(@RequestBody ScheduleGenerateRequest request) {
        log.info("API request received for schedule generation. Prompt: {}, auto-commit: {}", 
                request.getPrompt(), request.getCommit());
        
        String prompt = request.getPrompt() != null ? request.getPrompt() : "";
        ScheduleGenerateResponse response = orchestrationService.generateSchedule(prompt);

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
}
