package com.example.scheduler.service;

import com.example.scheduler.client.BubbleClient;
import com.example.scheduler.model.BubbleAvailability;
import com.example.scheduler.model.BubbleShift;
import com.example.scheduler.model.BubbleStore;
import com.example.scheduler.model.BubbleUser;
import com.example.scheduler.model.BubbleWageRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupabaseSyncService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseSyncService.class);

    private final BubbleClient bubbleClient;
    private final RestTemplate restTemplate;

    @Value("${supabase.api.url:}")
    private String supabaseUrl;

    @Value("${supabase.api.key:}")
    private String supabaseApiKey;

    public SupabaseSyncService(BubbleClient bubbleClient) {
        this.bubbleClient = bubbleClient;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Run the synchronization task automatically every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledSync() {
        log.info("Triggering scheduled Supabase database synchronization...");
        try {
            syncNow();
        } catch (Exception e) {
            log.error("Scheduled Supabase sync failed", e);
        }
    }

    /**
     * Perform the synchronization.
     */
    public synchronized String syncNow() {
        if (supabaseUrl == null || supabaseUrl.trim().isEmpty() || 
            supabaseApiKey == null || supabaseApiKey.trim().isEmpty()) {
            log.warn("Supabase sync skipped: SUPABASE_API_URL or SUPABASE_API_KEY is not configured.");
            return "Skipped: Configuration missing";
        }

        StringBuilder report = new StringBuilder("Sync report:\n");
        try {
            log.info("Starting Supabase database sync to host: {}", supabaseUrl);

            // 1. Sync Users
            List<BubbleUser> users = bubbleClient.fetchUsers();
            List<Map<String, Object>> userPayload = new ArrayList<>();
            for (BubbleUser u : users) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", u.getId());
                row.put("name", u.getName());
                row.put("role", u.getRole());
                row.put("max_hours", u.getMaxHours());
                row.put("active", u.getActive());
                userPayload.add(row);
            }
            sendPayload("bubble_users", userPayload);
            report.append("- Synced ").append(users.size()).append(" users.\n");

            // 2. Sync Stores
            List<BubbleStore> stores = bubbleClient.fetchStores();
            List<Map<String, Object>> storePayload = new ArrayList<>();
            for (BubbleStore s : stores) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", s.getId());
                row.put("name", s.getName());
                row.put("company", s.getCompany());
                row.put("availability_id", s.getAvailabilityId());
                row.put("is_deleted", s.getIsDeleted());
                storePayload.add(row);
            }
            sendPayload("bubble_stores", storePayload);
            report.append("- Synced ").append(stores.size()).append(" stores.\n");

            // 3. Sync Availability
            List<BubbleAvailability> availabilities = bubbleClient.fetchAvailability();
            List<Map<String, Object>> availabilityPayload = new ArrayList<>();
            for (BubbleAvailability a : availabilities) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", a.getId());
                row.put("thing_type", a.getThingType());
                row.put("thing_id", a.getThingId());
                row.put("available_days", a.getAvailableDays());
                row.put("workday_start_hour", a.getWorkdayStartHour());
                row.put("workday_end_hour", a.getWorkdayEndHour());
                row.put("weekend_start_hour", a.getWeekendStartHour());
                row.put("weekend_end_hour", a.getWeekendEndHour());
                availabilityPayload.add(row);
            }
            sendPayload("bubble_availability", availabilityPayload);
            report.append("- Synced ").append(availabilities.size()).append(" availability profiles.\n");

            // 4. Sync Wage Rates
            List<BubbleWageRate> wages = bubbleClient.fetchWageRates();
            List<Map<String, Object>> wagePayload = new ArrayList<>();
            for (BubbleWageRate w : wages) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", w.getId());
                row.put("company", w.getCompany());
                row.put("rate", w.getRate());
                row.put("user_id", w.getUser());
                wagePayload.add(row);
            }
            sendPayload("bubble_wage_rates", wagePayload);
            report.append("- Synced ").append(wages.size()).append(" wage rates.\n");

            // 5. Sync Shifts
            List<BubbleShift> shifts = bubbleClient.fetchShifts();
            List<Map<String, Object>> shiftPayload = new ArrayList<>();
            for (BubbleShift s : shifts) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", s.getId());
                row.put("assigned_user", s.getAssignedUser());
                row.put("start_time", s.getStartTime());
                row.put("end_time", s.getEndTime());
                row.put("notes", s.getNotes());
                row.put("assigned_company", s.getAssignedCompany());
                row.put("type", s.getType());
                row.put("status", s.getStatus());
                row.put("assigned_store", s.getAssignedStore());
                shiftPayload.add(row);
            }
            sendPayload("bubble_shifts", shiftPayload);
            report.append("- Synced ").append(shifts.size()).append(" shifts.\n");

            log.info("Supabase database sync completed successfully.");
            report.append("Status: Success");
            return report.toString();
        } catch (Exception e) {
            log.error("Sync to Supabase failed", e);
            return "Sync failed: " + e.getMessage();
        }
    }

    private void sendPayload(String tableName, List<Map<String, Object>> payload) {
        if (payload.isEmpty()) {
            return;
        }

        String url = String.format("%s/rest/v1/%s", supabaseUrl, tableName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        headers.set("Prefer", "resolution=merge-duplicates");

        HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(payload, headers);

        restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }
}
