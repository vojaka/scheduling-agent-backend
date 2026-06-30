package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.model.BubbleStore;
import com.comforthub.backoffice.model.BubbleUser;
import com.comforthub.backoffice.model.BubbleWageRate;
import com.comforthub.backoffice.model.entity.BubbleAvailabilityEntity;
import com.comforthub.backoffice.model.entity.BubbleCompanyEntity;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.model.entity.BubbleWageRateEntity;
import com.comforthub.backoffice.repository.BubbleAvailabilityRepository;
import com.comforthub.backoffice.repository.BubbleCompanyRepository;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.repository.BubbleWageRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Syncs data from the Bubble REST API into the PostgreSQL database via JPA/JDBC.
 * Runs automatically every hour and can be triggered manually via POST /api/schedule/sync.
 *
 * NOTE: This service will be removed once the React backoffice writes directly to PostgreSQL
 * and Bubble is decommissioned (Phase 5 of migration).
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
    private final BubbleCompanyRepository companyRepository;

    public BubbleSyncService(BubbleClient bubbleClient,
                             BubbleUserRepository userRepository,
                             BubbleStoreRepository storeRepository,
                             BubbleAvailabilityRepository availabilityRepository,
                             BubbleWageRateRepository wageRateRepository,
                             BubbleShiftRepository shiftRepository,
                             BubbleCompanyRepository companyRepository) {
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

            // 1. Users (load-or-create to preserve scoping columns set out-of-band)
            List<BubbleUserEntity> users = bubbleClient.fetchUsers().stream()
                    .map(this::toUserEntity)
                    .collect(Collectors.toList());
            userRepository.saveAll(users);
            report.append("- Synced ").append(users.size()).append(" users.\n");

            // 2. Stores
            List<BubbleStoreEntity> stores = bubbleClient.fetchStores().stream()
                    .map(this::toStoreEntity)
                    .collect(Collectors.toList());
            storeRepository.saveAll(stores);
            report.append("- Synced ").append(stores.size()).append(" stores.\n");

            // 3. Availability
            List<BubbleAvailabilityEntity> availabilities = bubbleClient.fetchAvailability().stream()
                    .map(this::toAvailabilityEntity)
                    .collect(Collectors.toList());
            availabilityRepository.saveAll(availabilities);
            report.append("- Synced ").append(availabilities.size()).append(" availability records.\n");

            // 4. Wage Rates
            List<BubbleWageRateEntity> wages = bubbleClient.fetchWageRates().stream()
                    .map(this::toWageRateEntity)
                    .collect(Collectors.toList());
            wageRateRepository.saveAll(wages);
            report.append("- Synced ").append(wages.size()).append(" wage rates.\n");

            // 5. Shifts
            List<BubbleShiftEntity> shifts = bubbleClient.fetchShifts().stream()
                    .map(this::toShiftEntity)
                    .collect(Collectors.toList());
            shiftRepository.saveAll(shifts);
            report.append("- Synced ").append(shifts.size()).append(" shifts.\n");

            // 6. Companies (owners/workers lists drive the rights check)
            List<BubbleCompanyEntity> companies = bubbleClient.fetchCompanies().stream()
                    .map(this::toCompanyEntity)
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toList());
            companyRepository.saveAll(companies);
            report.append("- Synced ").append(companies.size()).append(" companies.\n");

            log.info("Bubble → PostgreSQL sync completed.");
            report.append("Status: Success");
            return report.toString();
        } catch (Exception e) {
            log.error("Sync failed", e);
            return "Sync failed: " + e.getMessage();
        }
    }

    /**
     * Load-or-create so the scoping columns (auth0_user_id, and any backfilled
     * company_id) survive the hourly sync instead of being nulled out by a fresh
     * all-args construction. company_id is updated only when Bubble provides it.
     */
    private BubbleUserEntity toUserEntity(BubbleUser u) {
        BubbleUserEntity e = userRepository.findById(u.getId()).orElseGet(BubbleUserEntity::new);
        e.setId(u.getId());
        e.setName(u.getName());
        e.setRole(u.getRole());
        e.setMaxHours(u.getMaxHours());
        e.setActive(u.getActive());
        if (u.getCompanyId() != null && !u.getCompanyId().isBlank()) {
            e.setCompanyId(u.getCompanyId());
        }
        // auth0_user_id is never sourced from Bubble — preserved via load-or-create above.
        return e;
    }

    private BubbleStoreEntity toStoreEntity(BubbleStore s) {
        return new BubbleStoreEntity(s.getId(), s.getName(), s.getCompany(), s.getAvailabilityId(), s.getIsDeleted());
    }

    private BubbleAvailabilityEntity toAvailabilityEntity(BubbleAvailability a) {
        String[] days = a.getAvailableDays() == null ? null : a.getAvailableDays().toArray(new String[0]);
        return new BubbleAvailabilityEntity(
                a.getId(), a.getThingType(), a.getThingId(), days,
                a.getWorkdayStartHour(), a.getWorkdayEndHour(),
                a.getWeekendStartHour(), a.getWeekendEndHour());
    }

    private BubbleWageRateEntity toWageRateEntity(BubbleWageRate w) {
        return new BubbleWageRateEntity(w.getId(), w.getCompany(), w.getRate(), w.getUser());
    }

    private BubbleShiftEntity toShiftEntity(BubbleShift s) {
        return new BubbleShiftEntity(
                s.getId(), s.getAssignedUser(),
                parseOffset(s.getStartTime()), parseOffset(s.getEndTime()),
                s.getNotes(), s.getAssignedCompany(), s.getType(), s.getStatus(), s.getAssignedStore());
    }

    /**
     * Maps a raw Bubble company record. Bubble JSON keys vary, so several
     * candidate keys are tried for name/owners/workers. VERIFY against a real
     * /company API response; unmatched lists simply stay empty.
     */
    private BubbleCompanyEntity toCompanyEntity(Map<String, Object> m) {
        String id = asString(m.get("_id"));
        String name = asString(firstNonNull(m.get("name"), m.get("name_text")));
        String[] owners = asStringArray(firstNonNull(
                m.get("owners"), m.get("list_owners"), m.get("owners_list_user"), m.get("owners_list_users")));
        String[] workers = asStringArray(firstNonNull(
                m.get("workers"), m.get("list_workers"), m.get("workers_list_user"), m.get("workers_list_users")));
        return new BubbleCompanyEntity(id, name, owners, workers);
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
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

    /** Parses an ISO-8601 timestamp string from Bubble into an OffsetDateTime, tolerating both
     *  offset ("...+02:00") and instant ("...Z") forms. Returns null on missing/unparseable input. */
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
