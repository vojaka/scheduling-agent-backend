package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.dto.NotificationDto;
import com.comforthub.backoffice.service.CurrentUserService;
import com.comforthub.backoffice.service.SmartNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Controller exposing the scoped notifications and reminders matching the Bubble schema.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final SmartNotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(SmartNotificationService notificationService,
                                  CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications() {
        Optional<String> companyIdOpt = currentUserService.currentCompanyId();
        if (companyIdOpt.isEmpty()) {
            log.warn("Unauthenticated or company-less notification request.");
            return ResponseEntity.ok(Collections.emptyList());
        }
        String companyId = companyIdOpt.get();
        log.info("Fetching notifications for company {}", companyId);
        List<NotificationDto> list = notificationService.getNotificationsForCompany(companyId);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> markAsViewed(@PathVariable String id) {
        // If it is a dynamic reminder, it has a synthetic string ID prefix.
        // We do not save these viewed markers back to Bubble since they are computed locally.
        if (id.startsWith("stock-low-") || id.startsWith("shift-unassigned-") 
                || id.startsWith("worker-rate-missing-") || id.startsWith("order-pending-")
                || id.startsWith("booking-unassigned-")) {
            return ResponseEntity.ok().build();
        }

        try {
            notificationService.markAsViewed(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to mark notification {} as viewed", id, e);
            return ResponseEntity.status(500).build();
        }
    }
}
