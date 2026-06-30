package com.comforthub.backoffice.service;

import com.comforthub.backoffice.dto.ShiftResponseDto;
import com.comforthub.backoffice.dto.ShiftWriteRequest;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Backoffice write operations against the PostgreSQL system of record (shifts).
 * Shifts are addressed by their UUID surrogate id; backoffice-created shifts have
 * no Bubble id. Used by the CRUD endpoints and the schedule-commit dual-write path.
 */
@Service
public class ShiftService {

    private static final Logger log = LoggerFactory.getLogger(ShiftService.class);

    private final BubbleShiftRepository shiftRepository;

    public ShiftService(BubbleShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public List<BubbleShiftEntity> findAll() {
        return shiftRepository.findAll();
    }

    public Optional<BubbleShiftEntity> findById(UUID id) {
        return shiftRepository.findById(id);
    }

    @Transactional
    public BubbleShiftEntity create(ShiftWriteRequest request) {
        BubbleShiftEntity entity = new BubbleShiftEntity();
        applyRequest(entity, request);
        return shiftRepository.save(entity);
    }

    @Transactional
    public Optional<BubbleShiftEntity> update(UUID id, ShiftWriteRequest request) {
        return shiftRepository.findById(id).map(entity -> {
            applyRequest(entity, request);
            return shiftRepository.save(entity);
        });
    }

    @Transactional
    public boolean delete(UUID id) {
        if (!shiftRepository.existsById(id)) {
            return false;
        }
        shiftRepository.deleteById(id);
        return true;
    }

    @Transactional
    public void persistGenerated(ShiftResponseDto dto) {
        // Gemini-generated shift: keep its Bubble id (if any) in bubble_id; UUID id auto-generates.
        BubbleShiftEntity entity = dto.getId() != null
                ? shiftRepository.findByBubbleId(dto.getId()).orElseGet(BubbleShiftEntity::new)
                : new BubbleShiftEntity();
        if (dto.getId() != null) {
            entity.setBubbleId(dto.getId());
        }
        entity.setAssignedUser(dto.getAssignedUser());
        entity.setStartTime(parseOffset(dto.getStartTime()));
        entity.setEndTime(parseOffset(dto.getEndTime()));
        entity.setNotes(dto.getNotes());
        entity.setAssignedCompany(dto.getAssignedCompany());
        entity.setType(dto.getType());
        entity.setStatus(dto.getStatus());
        entity.setAssignedStore(dto.getAssignedStore());
        shiftRepository.save(entity);
    }

    private void applyRequest(BubbleShiftEntity entity, ShiftWriteRequest request) {
        OffsetDateTime start = parseOffset(request.getStartTime());
        OffsetDateTime end = parseOffset(request.getEndTime());
        if (start == null || end == null) {
            throw new IllegalArgumentException("startTime and endTime must be valid ISO-8601 timestamps");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        entity.setAssignedUser(request.getAssignedUser());
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setNotes(request.getNotes());
        entity.setAssignedCompany(request.getAssignedCompany());
        entity.setType(request.getType());
        entity.setStatus(request.getStatus());
        entity.setAssignedStore(request.getAssignedStore());
    }

    private OffsetDateTime parseOffset(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            try {
                return Instant.parse(value).atOffset(ZoneOffset.UTC);
            } catch (Exception e) {
                log.warn("Could not parse timestamp '{}'", value);
                return null;
            }
        }
    }
}
