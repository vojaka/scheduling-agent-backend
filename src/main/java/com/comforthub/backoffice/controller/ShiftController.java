package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.dto.ShiftWriteRequest;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.service.ShiftService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Backoffice write API for shifts, backed by PostgreSQL. Shifts are addressed by
 * their UUID id. Secured by the JWT resource server (SecurityConfig) — all paths
 * under /api require a valid token.
 */
@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private static final Logger log = LoggerFactory.getLogger(ShiftController.class);

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping
    public ResponseEntity<BubbleShiftEntity> create(@Valid @RequestBody ShiftWriteRequest request) {
        BubbleShiftEntity created = shiftService.create(request);
        log.info("Created shift {} for user {}", created.getId(), created.getAssignedUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BubbleShiftEntity> update(@PathVariable UUID id,
                                                    @Valid @RequestBody ShiftWriteRequest request) {
        return shiftService.update(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return shiftService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** 400 for invalid time ranges / unparseable timestamps surfaced by the service. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad Request", "message", ex.getMessage()));
    }
}
