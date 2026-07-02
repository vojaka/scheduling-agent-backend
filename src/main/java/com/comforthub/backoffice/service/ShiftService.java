package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Backoffice write operations for shifts.
 *
 * <p><b>#114 Bubble write-through:</b> Bubble is the source of truth, so every
 * create/update/delete is ALSO written to the Bubble {@code shift} type using
 * the exact raw field keys the ETL reads back ({@code sync.py#sync_shifts} /
 * {@link com.comforthub.backoffice.model.BubbleShift}). The PostgreSQL mirror
 * row is kept warm for Metabase and stores the Bubble id ({@code bubble_id}),
 * so the hourly ETL upsert lands on the same row (dedupe-safe). Bubble failures
 * are best-effort: logged loudly, the mirror write still happens.
 *
 * <p>Backoffice-created shifts are stamped with the caller's company so they
 * stay within the user's scope (see CurrentUserService).
 */
@Service
public class ShiftService {

    private static final Logger log = LoggerFactory.getLogger(ShiftService.class);

    /** Bubble Data API object type (the ETL {@code /shift} endpoint). */
    static final String BUBBLE_SHIFT_TYPE = "shift";

    // ===== VERIFIED raw Bubble shift keys — the exact aliases sync.py#sync_shifts
    // reads (also used by BubbleClient.BubbleShiftPostPayload, live in prod). =====
    static final String F_ASSIGNED_USER    = "assigned_user_user";
    static final String F_START_TIME       = "start_time_date";
    static final String F_END_TIME         = "end_time_date";
    static final String F_NOTES            = "notes_text";
    static final String F_ASSIGNED_COMPANY = "assigned_company_custom____merchant";
    static final String F_TYPE             = "type_option_shift_type";
    static final String F_STATUS           = "status_option_shift_approval_status";
    static final String F_ASSIGNED_STORE   = "assigned_store_custom_store";

    private final BubbleShiftRepository shiftRepository;
    private final CurrentUserService currentUserService;
    private final BubbleClient bubbleClient;

    public ShiftService(BubbleShiftRepository shiftRepository,
                        CurrentUserService currentUserService,
                        BubbleClient bubbleClient) {
        this.shiftRepository = shiftRepository;
        this.currentUserService = currentUserService;
        this.bubbleClient = bubbleClient;
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
        writeThroughCreate(entity);
        return shiftRepository.save(entity);
    }

    @Transactional
    public Optional<BubbleShiftEntity> update(UUID id, ShiftWriteRequest request) {
        Optional<String> companyIdOpt = currentUserService.currentCompanyId();
        return shiftRepository.findById(id).flatMap(entity -> {
            if (companyIdOpt.isPresent() && !companyIdOpt.get().equals(entity.getAssignedCompany())) {
                return Optional.empty();
            }
            applyRequest(entity, request);
            if (entity.getBubbleId() == null || entity.getBubbleId().isBlank()) {
                // A backoffice shift that never reached Bubble — heal by creating it now.
                writeThroughCreate(entity);
            } else {
                writeThroughUpdate(entity);
            }
            return Optional.of(shiftRepository.save(entity));
        });
    }

    @Transactional
    public boolean delete(UUID id) {
        Optional<String> companyIdOpt = currentUserService.currentCompanyId();
        return shiftRepository.findById(id).map(entity -> {
            if (companyIdOpt.isPresent() && !companyIdOpt.get().equals(entity.getAssignedCompany())) {
                return false;
            }
            writeThroughDelete(entity);
            shiftRepository.delete(entity);
            return true;
        }).orElse(false);
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

    // ------------------------------------------------- Bubble write-through

    /** #114: create the shift in Bubble and keep its id on the mirror row — best-effort. */
    private void writeThroughCreate(BubbleShiftEntity entity) {
        try {
            String bubbleId = bubbleClient.create(BUBBLE_SHIFT_TYPE, bubbleBody(entity));
            if (bubbleId != null && !bubbleId.isBlank()) {
                entity.setBubbleId(bubbleId);
            } else {
                log.error("Bubble shift create returned no id (user {}, start {}) — Bubble and the "
                        + "mirror are now OUT OF SYNC.", entity.getAssignedUser(), entity.getStartTime());
            }
        } catch (Exception e) {
            log.error("Bubble shift create FAILED (user {}, start {}) — Bubble and the mirror are "
                    + "now OUT OF SYNC: {}", entity.getAssignedUser(), entity.getStartTime(), e.getMessage());
        }
    }

    /** #114: PATCH the shift's fields onto its Bubble record — best-effort. */
    private void writeThroughUpdate(BubbleShiftEntity entity) {
        try {
            bubbleClient.update(BUBBLE_SHIFT_TYPE, entity.getBubbleId(), bubbleBody(entity));
        } catch (Exception e) {
            log.error("Bubble shift update FAILED for {} — Bubble is now STALE (mirror updated): {}",
                    entity.getBubbleId(), e.getMessage());
        }
    }

    /** #114: delete the shift's Bubble record — best-effort. */
    private void writeThroughDelete(BubbleShiftEntity entity) {
        if (entity.getBubbleId() == null || entity.getBubbleId().isBlank()) {
            return; // never existed in Bubble — nothing to delete there.
        }
        try {
            bubbleClient.delete(BUBBLE_SHIFT_TYPE, entity.getBubbleId());
        } catch (Exception e) {
            log.error("Bubble shift delete FAILED for {} — the record LINGERS in Bubble "
                    + "(mirror row removed): {}", entity.getBubbleId(), e.getMessage());
        }
    }

    /** POST/PATCH /obj/shift body using the VERIFIED raw keys; only non-null fields. */
    private static Map<String, Object> bubbleBody(BubbleShiftEntity entity) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, F_ASSIGNED_USER, entity.getAssignedUser());
        putIfPresent(body, F_START_TIME, toInstantString(entity.getStartTime()));
        putIfPresent(body, F_END_TIME, toInstantString(entity.getEndTime()));
        putIfPresent(body, F_NOTES, entity.getNotes());
        putIfPresent(body, F_ASSIGNED_COMPANY, entity.getAssignedCompany());
        putIfPresent(body, F_TYPE, entity.getType());
        putIfPresent(body, F_STATUS, entity.getStatus());
        putIfPresent(body, F_ASSIGNED_STORE, entity.getAssignedStore());
        return body;
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            body.put(key, value);
        }
    }

    private static String toInstantString(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
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
        // Stamp with the caller's company so writes stay within scope; fall back to the
        // request value if the user has no resolvable company (e.g. service callers).
        entity.setAssignedCompany(
                currentUserService.currentCompanyId().orElse(request.getAssignedCompany()));
        entity.setType(request.getType());
        entity.setStatus(request.getStatus());
        entity.setAssignedStore(request.getAssignedStore());
    }
}
