package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.model.BubbleStore;
import com.comforthub.backoffice.model.BubbleUser;
import com.comforthub.backoffice.model.BubbleWageRate;
import com.comforthub.backoffice.model.entity.BubbleAvailabilityEntity;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.model.entity.BubbleWageRateEntity;
import com.comforthub.backoffice.repository.BubbleAvailabilityRepository;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.repository.BubbleWageRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Syncs data from the Bubble REST API into PostgreSQL via JPA, upserting by the
 * Bubble natural key (bubble_id) so re-runs update rather than duplicate rows
 * under the new UUID surrogate primary keys.
 *
 * Removed once the React backoffice writes directly to PostgreSQL and Bubble is
 * decommissioned (Phase 5).
 */
@Service
public class BubbleSyncService {

    private static final Logger log = LoggerFactory.getLogger(BubbleSyncService.class);

    private final BubbleClient bubbleClient;
    private final BubbleUserRepository userRepository;
    private final BubbleStoreRepository storeRepository;
    private final BubbleAvailabilityRepository availabilityRepository;
    private final BubbleWageRateRepository wageRateRepository;
    private final BubbleShiftRepository shiftRepository;

    public BubbleSyncService(BubbleClient bubbleClient,
                             BubbleUserRepository userRepository,
                             BubbleStoreRepository storeRepository,
                             BubbleAvailabilityRepository availabilityRepository,
                             BubbleWageRateRepository wageRateRepository,
                             BubbleShiftRepository shiftRepository) {
        this.bubbleClient = bubbleClient;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.availabilityRepository = availabilityRepository;
        this.wageRateRepository = wageRateRepository;
        this.shiftRepository = shiftRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledSync() {
        log.info("Triggering scheduled Bubble → PostgreSQL sync...");
        try {
            syncNow();
        } catch (Exception e) {
            log.error("Scheduled sync failed", e);
        }
    }

    @Transactional
    public synchronized String syncNow() {
        StringBuilder report = new StringBuilder("Sync report:\n");
        try {
            int u = 0, s = 0, a = 0, w = 0, sh = 0;

            for (BubbleUser x : bubbleClient.fetchUsers()) { upsertUser(x); u++; }
            report.append("- Synced ").append(u).append(" users.\n");

            for (BubbleStore x : bubbleClient.fetchStores()) { upsertStore(x); s++; }
            report.append("- Synced ").append(s).append(" stores.\n");

            for (BubbleAvailability x : bubbleClient.fetchAvailability()) { upsertAvailability(x); a++; }
            report.append("- Synced ").append(a).append(" availability records.\n");

            for (BubbleWageRate x : bubbleClient.fetchWageRates()) { upsertWageRate(x); w++; }
            report.append("- Synced ").append(w).append(" wage rates.\n");

            for (BubbleShift x : bubbleClient.fetchShifts()) { upsertShift(x); sh++; }
            report.append("- Synced ").append(sh).append(" shifts.\n");

            log.info("Bubble → PostgreSQL sync completed.");
            report.append("Status: Success");
            return report.toString();
        } catch (Exception e) {
            log.error("Sync failed", e);
            return "Sync failed: " + e.getMessage();
        }
    }

    private void upsertUser(BubbleUser x) {
        BubbleUserEntity e = userRepository.findByBubbleId(x.getId()).orElseGet(BubbleUserEntity::new);
        e.setBubbleId(x.getId());
        e.setFullName(x.getName());
        e.setRole(x.getRole());
        e.setMaxHours(x.getMaxHours() == null ? null : BigDecimal.valueOf(x.getMaxHours()));
        e.setIsActive(x.getActive());
        userRepository.save(e);
    }

    private void upsertStore(BubbleStore x) {
        BubbleStoreEntity e = storeRepository.findByBubbleId(x.getId()).orElseGet(BubbleStoreEntity::new);
        e.setBubbleId(x.getId());
        e.setName(x.getName());
        e.setCompany(x.getCompany());
        e.setAvailabilityId(x.getAvailabilityId());
        e.setIsDeleted(x.getIsDeleted());
        storeRepository.save(e);
    }

    private void upsertAvailability(BubbleAvailability x) {
        BubbleAvailabilityEntity e = availabilityRepository.findByBubbleId(x.getId()).orElseGet(BubbleAvailabilityEntity::new);
        e.setBubbleId(x.getId());
        e.setThingType(x.getThingType());
        e.setThingId(x.getThingId());
        e.setAvailableDays(x.getAvailableDays() == null ? null : x.getAvailableDays().toArray(new String[0]));
        e.setWorkdayStartHour(x.getWorkdayStartHour());
        e.setWorkdayEndHour(x.getWorkdayEndHour());
        e.setWeekendStartHour(x.getWeekendStartHour());
        e.setWeekendEndHour(x.getWeekendEndHour());
        availabilityRepository.save(e);
    }

    private void upsertWageRate(BubbleWageRate x) {
        BubbleWageRateEntity e = wageRateRepository.findByBubbleId(x.getId()).orElseGet(BubbleWageRateEntity::new);
        e.setBubbleId(x.getId());
        e.setCompany(x.getCompany());
        e.setRate(x.getRate());
        e.setUserId(x.getUser());
        wageRateRepository.save(e);
    }

    private void upsertShift(BubbleShift x) {
        BubbleShiftEntity e = shiftRepository.findByBubbleId(x.getId()).orElseGet(BubbleShiftEntity::new);
        e.setBubbleId(x.getId());
        e.setAssignedUser(x.getAssignedUser());
        e.setStartTime(parseOffset(x.getStartTime()));
        e.setEndTime(parseOffset(x.getEndTime()));
        e.setNotes(x.getNotes());
        e.setAssignedCompany(x.getAssignedCompany());
        e.setType(x.getType());
        e.setStatus(x.getStatus());
        e.setAssignedStore(x.getAssignedStore());
        shiftRepository.save(e);
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
                log.warn("Could not parse timestamp '{}', storing null", value);
                return null;
            }
        }
    }
}
