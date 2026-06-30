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
import com.comforthub.backoffice.model.entity.CompanyEntity;
import com.comforthub.backoffice.repository.BubbleAvailabilityRepository;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.repository.BubbleWageRateRepository;
import com.comforthub.backoffice.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Syncs data from the Bubble REST API into PostgreSQL via JPA, upserting by the
 * Bubble natural key (bubble_id) so re-runs update rather than duplicate rows
 * under the UUID surrogate primary keys introduced in V3.
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
    private final CompanyRepository companyRepository;

    public BubbleSyncService(BubbleClient bubbleClient,
                             BubbleUserRepository userRepository,
                             BubbleStoreRepository storeRepository,
                             BubbleAvailabilityRepository availabilityRepository,
                             BubbleWageRateRepository wageRateRepository,
                             BubbleShiftRepository shiftRepository,
                             CompanyRepository companyRepository) {
        this.bubbleClient = bubbleClient;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.availabilityRepository = availabilityRepository;
        this.wageRateRepository = wageRateRepository;
        this.shiftRepository = shiftRepository;
        this.companyRepository = companyRepository;
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
            log.info("Starting Bubble → PostgreSQL sync via JPA...");
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

            int c = 0;
            for (Map<String, Object> x : bubbleClient.fetchCompanies()) { upsertCompany(x); c++; }
            report.append("- Synced ").append(c).append(" companies.\n");

            log.info("Bubble → PostgreSQL sync completed.");
            report.append("Status: Success");
            return report.toString();
        } catch (Exception e) {
            log.error("Sync failed", e);
            return "Sync failed: " + e.getMessage();
        }
    }
    /**
     * Load-or-create by bubble_id so the UUID PK is preserved across runs and the
     * scoping columns (auth0_user_id, and any backfilled company_id) survive the
     * hourly sync. company_id is updated only when Bubble actually provides it.
     */
    private void upsertUser(BubbleUser x) {
        BubbleUserEntity e = userRepository.findByBubbleId(x.getId()).orElseGet(BubbleUserEntity::new);
        e.setBubbleId(x.getId());
        e.setFullName(x.getName());
        e.setRole(x.getRole());
        e.setMaxHours(x.getMaxHours() == null ? null : BigDecimal.valueOf(x.getMaxHours()));
        e.setIsActive(x.getActive());
        e.setEmail(x.getEmailAddress());
        if (x.getCompanyId() != null && !x.getCompanyId().isBlank()) {
            e.setCompanyId(x.getCompanyId());
        }
        // auth0_user_id is never sourced from Bubble — preserved via load-or-create above.
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

    private void upsertCompany(Map<String, Object> m) {
        String bubbleId = asString(m.get("_id"));
        if (bubbleId == null || bubbleId.isBlank()) {
            return;
        }
        CompanyEntity e = companyRepository.findById(bubbleId).orElseGet(CompanyEntity::new);
        e.setId(bubbleId);
        e.setName(asString(firstNonNull(m.get("Brand Company Name"), m.get("name"), m.get("name_text"))));
        e.setOwners(asStringArray(firstNonNull(
                m.get("Owners"), m.get("owners"), m.get("list_owners"), m.get("owners_list_user"), m.get("owners_list_users"))));
        e.setWorkers(asStringArray(firstNonNull(
                m.get("Workers (list)"), m.get("workers"), m.get("list_workers"), m.get("workers_list_user"), m.get("workers_list_users"))));
        e.setIsDeleted(asBoolean(firstNonNull(m.get("isDeleted"), m.get("is_deleted"), m.get("Deleted"))));
        companyRepository.save(e);
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Boolean asBoolean(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        return Boolean.valueOf(o.toString());
    }

    private static Object firstNonNull(Object... os) {
        for (Object o : os) {
            if (o != null) {
                return o;
            }
        }
        return null;
    }

    private static String[] asStringArray(Object o) {
        if (o instanceof Collection) {
            Collection<?> c = (Collection<?>) o;
            return c.stream().filter(Objects::nonNull).map(Object::toString).toArray(String[]::new);
        }
        return o == null ? new String[0] : new String[]{o.toString()};
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
