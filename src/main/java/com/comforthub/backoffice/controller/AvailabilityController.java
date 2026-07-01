package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.AvailabilityDto;
import com.comforthub.backoffice.mapper.AvailabilityBubbleMapper;
import com.comforthub.backoffice.mapper.StoreBubbleMapper;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Availability CRUD — the opening hours/days profile attached to a store or
 * worker. New in Phase 5 (had no controller before; only the hourly ETL synced
 * it into PostgreSQL for analytics).
 *
 * <p>Bubble is the source of truth. This controller <b>proxies the Bubble Data
 * API</b> and never touches PostgreSQL for writes. Availability records carry no
 * company field of their own, so every call is scoped by resolving the
 * <b>thing</b> they belong to:
 * <ul>
 *   <li>{@code store} → the store's {@code Company} must match the caller's
 *       company (via the Bubble {@code store} record);</li>
 *   <li>{@code worker}/{@code user} → the user's company must match (via the
 *       synced user mirror — the same authoritative user→company mapping
 *       {@link CurrentUserService} uses for all scoping, which avoids inferring a
 *       Bubble user→company field key).</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/availability?thingType=&thingId=} — the thing's profile,
 *       or 404 if none exists yet.</li>
 *   <li>{@code PUT /api/availability/{id}} — upsert. Updates the record when
 *       {@code id} is a real availability the caller may touch; otherwise
 *       <b>lazy-creates</b> one for {@code (thingType, thingId)} from the body
 *       (updating an existing profile for that thing if one is already there, so
 *       no duplicates).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final BubbleClient bubbleClient;
    private final AvailabilityBubbleMapper mapper;
    private final StoreBubbleMapper storeMapper;
    private final BubbleUserRepository userRepository;
    private final CurrentUserService currentUserService;

    public AvailabilityController(BubbleClient bubbleClient,
                                  AvailabilityBubbleMapper mapper,
                                  StoreBubbleMapper storeMapper,
                                  BubbleUserRepository userRepository,
                                  CurrentUserService currentUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.storeMapper = storeMapper;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    /** The availability profile for one thing, or 404 if none exists yet. */
    @GetMapping
    public ResponseEntity<AvailabilityDto> getAvailability(@RequestParam String thingType,
                                                           @RequestParam String thingId) {
        String companyId = currentUserService.currentCompanyId().orElse(null);
        if (companyId == null || !ownsThing(companyId, thingType, thingId)) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> record = findByThing(thingType, thingId);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapper.toDto(record));
    }

    /**
     * Upsert the availability profile. Updates {@code id} when it is a real
     * availability whose thing belongs to the caller's company; otherwise
     * lazy-creates (or updates the thing's existing profile) from the body.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AvailabilityDto> upsertAvailability(@PathVariable String id,
                                                              @RequestBody AvailabilityDto body) {
        String companyId = currentUserService.currentCompanyId().orElse(null);
        if (companyId == null) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> existing = bubbleClient.get(AvailabilityBubbleMapper.TYPE, id);
        if (existing != null) {
            // Update by id — the record's own thing is authoritative for scoping.
            String thingType = firstNonBlank(mapper.thingTypeOf(existing), body.getThingType());
            String thingId = firstNonBlank(mapper.thingIdOf(existing), body.getThingId());
            if (!ownsThing(companyId, thingType, thingId)) {
                return ResponseEntity.notFound().build();
            }
            bubbleClient.update(AvailabilityBubbleMapper.TYPE, id, mapper.toUpdateBody(body));
            return ResponseEntity.ok(reload(id, body));
        }

        // Lazy-create path — the body must identify the thing, which must be owned.
        String thingType = body.getThingType();
        String thingId = body.getThingId();
        if (isBlank(thingType) || isBlank(thingId)) {
            return ResponseEntity.badRequest().build();
        }
        if (!ownsThing(companyId, thingType, thingId)) {
            return ResponseEntity.notFound().build();
        }
        // Upsert, not blind-create: reuse the thing's existing profile if present.
        Map<String, Object> current = findByThing(thingType, thingId);
        if (current != null) {
            String currentId = readId(current);
            bubbleClient.update(AvailabilityBubbleMapper.TYPE, currentId, mapper.toUpdateBody(body));
            return ResponseEntity.ok(reload(currentId, body));
        }
        String newId = bubbleClient.create(AvailabilityBubbleMapper.TYPE, mapper.toCreateBody(body));
        return ResponseEntity.ok(reload(newId, body));
    }

    // ------------------------------------------------------------- helpers

    /** The single availability record for a thing, or {@code null} if none. */
    private Map<String, Object> findByThing(String thingType, String thingId) {
        BubbleListResult result = bubbleClient.list(
                AvailabilityBubbleMapper.TYPE, mapper.byThing(thingType, thingId), 0, 1);
        List<Map<String, Object>> results = result.getResults();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Whether the caller's company owns the thing the availability is attached to.
     * {@code store} resolves via the Bubble store record; {@code worker}/{@code user}
     * via the synced user→company mirror.
     */
    private boolean ownsThing(String companyId, String thingType, String thingId) {
        if (isBlank(thingType) || isBlank(thingId)) {
            return false;
        }
        String t = thingType.trim().toLowerCase();
        if (t.startsWith("store")) {
            Map<String, Object> store = bubbleClient.get(StoreBubbleMapper.TYPE, thingId);
            return store != null && companyId.equals(storeMapper.companyOf(store));
        }
        if (t.startsWith("worker") || t.startsWith("user")) {
            return userRepository.findByBubbleId(thingId)
                    .map(u -> companyId.equals(u.getCompanyId()))
                    .orElse(false);
        }
        return false;
    }

    /**
     * Re-fetch from Bubble so the response reflects persisted state. Falls back to
     * the request body (with the id set) if the read-back fails.
     */
    private AvailabilityDto reload(String id, AvailabilityDto fallback) {
        if (id != null) {
            Map<String, Object> record = bubbleClient.get(AvailabilityBubbleMapper.TYPE, id);
            if (record != null) {
                return mapper.toDto(record);
            }
        }
        if (fallback != null) {
            fallback.setId(id);
            fallback.setBubbleId(id);
        }
        return fallback;
    }

    private static String readId(Map<String, Object> record) {
        Object v = record.get("_id");
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : b;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
