package com.example.scheduler.service;

import com.example.scheduler.client.BubbleClient;
import com.example.scheduler.dto.ScheduleGenerateResponse;
import com.example.scheduler.dto.ShiftResponseDto;
import com.example.scheduler.dto.ValidationReport;
import com.example.scheduler.model.BubbleAvailability;
import com.example.scheduler.model.BubbleShift;
import com.example.scheduler.model.BubbleStore;
import com.example.scheduler.model.BubbleUser;
import com.example.scheduler.model.BubbleWageRate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final BubbleClient bubbleClient;
    private final DeterministicValidator validator;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.model}")
    private String geminiModel;

    public OrchestrationService(BubbleClient bubbleClient, 
                                DeterministicValidator validator, 
                                ObjectMapper objectMapper) {
        this.bubbleClient = bubbleClient;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public ScheduleGenerateResponse generateSchedule(String userPrompt, String month, String companyId,
                                                      List<String> workerIds,
                                                      Integer bufferBeforeMinutes, Integer bufferAfterMinutes) {
        List<String> logs = new ArrayList<>();
        logs.add("Initiating schedule generation...");
        
        // 1. Fetch data from Bubble
        logs.add("Fetching worker and wage context from Bubble...");
        List<BubbleUser> workers = bubbleClient.fetchUsers();
        List<BubbleWageRate> wages = bubbleClient.fetchWageRates();
        List<BubbleStore> stores = bubbleClient.fetchStores();
        List<BubbleAvailability> allAvailability = bubbleClient.fetchAvailability();

        logs.add("Successfully fetched " + workers.size() + " workers, " + wages.size() + " wage rates, "
                + stores.size() + " stores, " + allAvailability.size() + " availability records.");

        // Filter workers if workerIds is provided and not empty
        if (workerIds != null && !workerIds.isEmpty()) {
            workers = workers.stream()
                    .filter(w -> workerIds.contains(w.getId()))
                    .collect(Collectors.toList());
            logs.add("Filtered workers list to " + workers.size() + " specified workers based on input parameter.");
        }

        // Resolve store availability (opening hours) for the target company
        BubbleAvailability storeAvailability = null;
        if (companyId != null && !companyId.trim().isEmpty()) {
            // Find the store record that belongs to this company
            String storeAvailabilityId = stores.stream()
                    .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                    .filter(s -> companyId.equals(s.getCompany()))
                    .map(BubbleStore::getAvailabilityId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);

            if (storeAvailabilityId != null) {
                storeAvailability = allAvailability.stream()
                        .filter(a -> storeAvailabilityId.equals(a.getId()))
                        .findFirst().orElse(null);
            }

            if (storeAvailability != null) {
                logs.add("Resolved store opening hours: workdays " + storeAvailability.getWorkdayStartHour()
                        + ":00-" + storeAvailability.getWorkdayEndHour() + ":00, weekends "
                        + storeAvailability.getWeekendStartHour() + ":00-" + storeAvailability.getWeekendEndHour()
                        + ":00, open days: " + storeAvailability.getAvailableDays());
            } else {
                logs.add("No store availability found for company " + companyId + " — shifts will use default hours.");
            }
        }
        if (companyId != null && !companyId.trim().isEmpty()) {
            java.util.Set<String> workersWithWageForCompany = wages.stream()
                    .filter(rate -> companyId.equals(rate.getCompany()))
                    .map(BubbleWageRate::getUser)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

            workers = workers.stream()
                    .filter(w -> workersWithWageForCompany.contains(w.getId()))
                    .collect(Collectors.toList());

            wages = wages.stream()
                    .filter(rate -> companyId.equals(rate.getCompany()))
                    .collect(Collectors.toList());

            logs.add("Filtered worker pool to " + workers.size() + " workers who have valid wage rates for store/company: " + companyId);
        }

        // Date range determination
        ZoneId estoniaZone = ZoneId.of("Europe/Tallinn");
        ZonedDateTime now = ZonedDateTime.now(estoniaZone);
        ZonedDateTime nextMonday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        String nextMondayStr = nextMonday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        String startLocalDate = nextMondayStr;
        String endLocalDate = nextMonday.plusDays(6).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String schedulePeriodStr = "the upcoming week starting on Monday, " + nextMondayStr;

        if (month != null && !month.trim().isEmpty()) {
            try {
                YearMonth ym;
                if (month.contains(" ")) {
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH);
                    ym = YearMonth.parse(month, fmt);
                } else if (month.contains("-")) {
                    ym = YearMonth.parse(month);
                } else {
                    throw new IllegalArgumentException("Unknown month format: " + month);
                }
                LocalDate start = ym.atDay(1);
                LocalDate end = ym.atEndOfMonth();
                startLocalDate = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                endLocalDate = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                schedulePeriodStr = "the entire month of " + ym.getMonth().name() + " " + ym.getYear() + " (from " + startLocalDate + " to " + endLocalDate + ")";
            } catch (Exception e) {
                logs.add("WARNING: Failed to parse month '" + month + "', falling back to next week. Error: " + e.getMessage());
            }
        }

        List<BubbleShift> proposedShifts;

        // Check if we should run in live Gemini mode or fallback to simulation
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.equals("default-gemini-key")) {
            logs.add("[SIMULATION MODE] Gemini API Key is not set. Running simulated schedule generation...");
            proposedShifts = runSimulatedLLM(userPrompt, workers, startLocalDate, logs);
        } else {
            logs.add("[LIVE MODE] Running real Gemini model generation via API...");
            proposedShifts = runRealGemini(userPrompt, workers, wages, storeAvailability,
                    bufferBeforeMinutes, bufferAfterMinutes,
                    startLocalDate, endLocalDate, schedulePeriodStr, logs);
        }

        // Enrich shifts with company and type before validation
        enrichShifts(proposedShifts, workers, wages, companyId);

        // 2. Run Deterministic Validation (Estonian labor laws & constraints)
        logs.add("Sending proposed shifts to Deterministic Validator Gatekeeper...");
        ValidationReport report = validator.validate(proposedShifts, workers);
        
        if (report.isValid()) {
            logs.add("VALIDATION SUCCESS: Proposed schedule matches all Estonian compliance rules.");
        } else {
            logs.add("VALIDATION FAILED: Found " + report.getIssues().size() + " compliance violations.");
        }

        // 3. Assemble response
        List<ShiftResponseDto> responseDtos = proposedShifts.stream()
                .map(ShiftResponseDto::new)
                .collect(Collectors.toList());

        return new ScheduleGenerateResponse(responseDtos, report, logs);
    }

    private List<BubbleShift> runRealGemini(String userPrompt, List<BubbleUser> workers, List<BubbleWageRate> wages,
                                            BubbleAvailability storeAvailability,
                                            Integer bufferBeforeMinutes, Integer bufferAfterMinutes,
                                            String startLocalDate, String endLocalDate, String schedulePeriodStr, List<String> logs) {
        try {
            ZoneId estoniaZone = ZoneId.of("Europe/Tallinn");
            ZonedDateTime now = ZonedDateTime.now(estoniaZone);
            String todayStr = now.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd"));

            // Build per-worker contracted hours context
            // Statutory hours = number of Mon-Fri workdays in the period × 8h
            // Contracted target = FTE (rate) × statutory hours
            // Store open days (from availability) constrain which days can actually have shifts
            LocalDate periodStart = LocalDate.parse(startLocalDate);
            LocalDate periodEnd   = LocalDate.parse(endLocalDate);

            // Determine which day-of-week names the store is open (normalise to English title-case)
            java.util.Set<String> storeOpenDayNames = new java.util.HashSet<>();
            if (storeAvailability != null && storeAvailability.getAvailableDays() != null) {
                storeAvailability.getAvailableDays().forEach(d -> storeOpenDayNames.add(d.trim()));
            } else {
                // No restriction — all days are open
                storeOpenDayNames.addAll(Arrays.asList(
                        "Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"));
            }

            // Count statutory workdays (Mon-Fri) in period
            long statutoryWorkdays = 0;
            LocalDate cursor = periodStart;
            while (!cursor.isAfter(periodEnd)) {
                DayOfWeek dow = cursor.getDayOfWeek();
                if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                    statutoryWorkdays++;
                }
                cursor = cursor.plusDays(1);
            }
            double statutoryHours = statutoryWorkdays * 8.0;

            // Count store-open days in period (for actual scheduling capacity)
            long storeOpenDaysInPeriod = 0;
            cursor = periodStart;
            while (!cursor.isAfter(periodEnd)) {
                String dayName = cursor.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
                if (storeOpenDayNames.contains(dayName)) {
                    storeOpenDaysInPeriod++;
                }
                cursor = cursor.plusDays(1);
            }

            // Build wage lookup: workerId → FTE rate
            Map<String, Double> workerFteMap = wages.stream()
                    .filter(r -> r.getUser() != null && r.getRate() != null)
                    .collect(Collectors.toMap(
                            BubbleWageRate::getUser,
                            BubbleWageRate::getRate,
                            (a, b) -> a)); // keep first if duplicates

            logs.add(String.format(
                    "Scheduling period: %s to %s | Statutory workdays: %d (%.0f h) | Store-open days in period: %d",
                    startLocalDate, endLocalDate, statutoryWorkdays, statutoryHours, storeOpenDaysInPeriod));

            // Build prompt context: one line per worker with contracted target
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("Worker contracted hours for this period:\n");
            for (BubbleUser w : workers) {
                double fte = workerFteMap.getOrDefault(w.getId(), 1.0);
                double contractedHours = Math.round(fte * statutoryHours * 10.0) / 10.0;
                // Max shift hours per open day (capped at store window)
                double avgHoursPerShift = 8.0; // sensible default
                long recommendedShifts = Math.round(contractedHours / avgHoursPerShift);
                contextBuilder.append(String.format(
                        "- Name: %s (ID: %s) | FTE: %.2f | Contracted hours this period: %.1f h | Recommended shifts: ~%d\n",
                        w.getName(), w.getId(), fte, contractedHours, recommendedShifts));
            }

            contextBuilder.append(String.format(
                    "\nStore open days in this period: %d out of %d total days.\n",
                    storeOpenDaysInPeriod, periodEnd.getDayOfMonth()));
            contextBuilder.append("Open day names: ").append(String.join(", ", storeOpenDayNames)).append("\n");
            contextBuilder.append("\nWage Rates (for reference):\n");
            for (BubbleWageRate r : wages) {
                contextBuilder.append(String.format("- Worker: %s, FTE: %.2f\n",
                        r.getUser(), r.getRate() != null ? r.getRate() : 1.0));
            }

            // Inject store opening hours + buffer context if available
            StringBuilder hoursContext = new StringBuilder();
            if (storeAvailability != null) {
                int bufBefore = bufferBeforeMinutes != null ? bufferBeforeMinutes : 0;
                int bufAfter  = bufferAfterMinutes  != null ? bufferAfterMinutes  : 0;

                hoursContext.append("\nStore Opening Hours:\n");
                List<String> openDays = storeAvailability.getAvailableDays();
                if (openDays != null && !openDays.isEmpty()) {
                    hoursContext.append("- Open days: ").append(String.join(", ", openDays)).append("\n");
                } else {
                    hoursContext.append("- Open days: all days\n");
                }
                if (storeAvailability.getWorkdayStartHour() != null && storeAvailability.getWorkdayEndHour() != null) {
                    // Effective shift window = opening - bufBefore to closing + bufAfter
                    int wdStart = storeAvailability.getWorkdayStartHour() * 60 - bufBefore;
                    int wdEnd   = storeAvailability.getWorkdayEndHour()   * 60 + bufAfter;
                    hoursContext.append(String.format("- Workday shift window: %02d:%02d to %02d:%02d",
                            wdStart / 60, wdStart % 60, wdEnd / 60, wdEnd % 60)).append("\n");
                }
                if (storeAvailability.getWeekendStartHour() != null && storeAvailability.getWeekendEndHour() != null) {
                    int weStart = storeAvailability.getWeekendStartHour() * 60 - bufBefore;
                    int weEnd   = storeAvailability.getWeekendEndHour()   * 60 + bufAfter;
                    hoursContext.append(String.format("- Weekend shift window: %02d:%02d to %02d:%02d",
                            weStart / 60, weStart % 60, weEnd / 60, weEnd % 60)).append("\n");
                }
                if (bufBefore > 0) {
                    hoursContext.append("- Workers may start up to ").append(bufBefore)
                            .append(" minutes BEFORE store opening for preparation/setup.\n");
                }
                if (bufAfter > 0) {
                    hoursContext.append("- Workers may stay up to ").append(bufAfter)
                            .append(" minutes AFTER store closing for cleanup/handover.\n");
                }
                hoursContext.append("IMPORTANT: Shifts MUST start at or after the workday/weekend shift window start, "
                        + "and MUST end at or before the workday/weekend shift window end.\n");
            }

            String systemPrompt = "You are an expert workforce scheduling agent. Today's date is " + todayStr + ". " +
                    "Your job is to generate a complete shift schedule for " + schedulePeriodStr + ".\n" +
                    "CRITICAL GOAL: Each worker must accumulate EXACTLY their contracted hours for this period. " +
                    "Distribute their shifts evenly across the available store-open days, respecting all constraints below.\n" +
                    "You must output a JSON object containing an array 'proposedShifts'. Each item in 'proposedShifts' must contain:\n" +
                    "- 'assignedUser': The exact name or ID of the assigned worker.\n" +
                    "- 'startTime': ISO-8601 UTC datetime string (between " + startLocalDate + "T00:00:00Z and " + endLocalDate + "T23:59:59Z).\n" +
                    "- 'endTime': ISO-8601 UTC datetime string (between " + startLocalDate + "T00:00:00Z and " + endLocalDate + "T23:59:59Z).\n" +
                    "- 'notes': Brief comment about the shift (e.g. 'Standard shift', 'Pre-opening prep').\n\n" +
                    "Strict Compliance Rules (Estonia):\n" +
                    "1. No single shift may be longer than 12 hours.\n" +
                    "2. Each worker must have at least 11 hours of continuous rest between consecutive shifts.\n" +
                    "3. Minimize night shifts (22:00–06:00) where possible.\n" +
                    "4. Shifts ONLY on days the store is open (see store context below).\n" +
                    "5. Total hours scheduled per worker MUST closely match their contracted hours for the period.\n" +
                    (hoursContext.length() > 0 ? "\nStore Scheduling Constraints:\n" + hoursContext + "\n" : "") +
                    "\nUser Custom Guidelines:\n" +
                    userPrompt;

            logs.add("Assembled prompt context with " + workers.size() + " workers.");
            
            // Build HTTP Request payload
            Map<String, Object> requestBody = new HashMap<>();
            
            Map<String, Object> part = new HashMap<>();
            part.put("text", systemPrompt + "\n\nWorker Context:\n" + contextBuilder.toString());
            
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));
            requestBody.put("contents", List.of(content));

            // Force responseSchema
            Map<String, Object> responseSchema = new HashMap<>();
            responseSchema.put("type", "OBJECT");
            
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> proposedShiftsSchema = new HashMap<>();
            proposedShiftsSchema.put("type", "ARRAY");
            
            Map<String, Object> itemsSchema = new HashMap<>();
            itemsSchema.put("type", "OBJECT");
            
            Map<String, Object> itemProps = new HashMap<>();
            itemProps.put("assignedUser", Map.of("type", "STRING"));
            itemProps.put("startTime", Map.of("type", "STRING"));
            itemProps.put("endTime", Map.of("type", "STRING"));
            itemProps.put("notes", Map.of("type", "STRING"));
            
            itemsSchema.put("properties", itemProps);
            itemsSchema.put("required", List.of("assignedUser", "startTime", "endTime"));
            
            proposedShiftsSchema.put("items", itemsSchema);
            properties.put("proposedShifts", proposedShiftsSchema);
            
            responseSchema.put("properties", properties);
            responseSchema.put("required", List.of("proposedShifts"));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("responseSchema", responseSchema);
            requestBody.put("generationConfig", generationConfig);

            // Execute POST request to Gemini REST API
            String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", 
                    geminiModel, geminiApiKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            logs.add("Calling Gemini API (" + geminiModel + ")...");
            String responseStr = restTemplate.postForObject(url, entity, String.class);
            
            logs.add("Received response from Gemini.");

            // Parse Gemini Response
            GeminiResponse geminiResponse = objectMapper.readValue(responseStr, GeminiResponse.class);
            if (geminiResponse != null && geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()) {
                String text = geminiResponse.getCandidates().get(0).getContent().getParts().get(0).getText();
                logs.add("Raw LLM Output: " + text);

                GeminiOutput parsedJson = objectMapper.readValue(text, GeminiOutput.class);
                if (parsedJson != null && parsedJson.getProposedShifts() != null) {
                    List<BubbleShift> shifts = new ArrayList<>();
                    for (GeminiShift gs : parsedJson.getProposedShifts()) {
                        BubbleShift bs = new BubbleShift();
                        bs.setAssignedUser(gs.getAssignedUser());
                        bs.setStartTime(gs.getStartTime());
                        bs.setEndTime(gs.getEndTime());
                        bs.setNotes(gs.getNotes());
                        shifts.add(bs);
                    }
                    return shifts;
                }
            }

            logs.add("ERROR: Empty or malformed candidates response from Gemini API.");
        } catch (Exception e) {
            logs.add("ERROR: Real Gemini generation failed: " + e.getMessage());
            log.error("Gemini API call error", e);
        }

        logs.add("Falling back to simulation mode due to error...");
        return runSimulatedLLM(userPrompt, workers, startLocalDate, logs);
    }

    private List<BubbleShift> runSimulatedLLM(String prompt, List<BubbleUser> workers, String startLocalDate, List<String> logs) {
        List<BubbleShift> shifts = new ArrayList<>();
        
        // Define standard week starting Monday in the future
        Instant mondayEight = Instant.parse(startLocalDate + "T08:00:00Z");
        
        // Pick names from workers, fallback to defaults if list is empty
        String name1 = !workers.isEmpty() ? workers.get(0).getName() : "Tom";
        String name2 = workers.size() > 1 ? workers.get(1).getName() : "Lily";

        if (prompt != null && (prompt.toLowerCase().contains("fail") || prompt.toLowerCase().contains("violation"))) {
            logs.add("Prompt requested violations. Generating simulated schedule with Estonian labor law violations:");
            logs.add("- Violation 1: " + name1 + " will have a shift of 13 hours (limit is 12h).");
            logs.add("- Violation 2: " + name2 + " will have only 8 hours of rest between consecutive shifts (limit is 11h).");

            // Worker 1: 13-hour shift (Violates Max Duration)
            BubbleShift shift1 = new BubbleShift();
            shift1.setAssignedUser(name1);
            shift1.setStartTime(mondayEight.toString());
            shift1.setEndTime(mondayEight.plus(13, ChronoUnit.HOURS).toString());
            shift1.setNotes("Long day shift");
            shifts.add(shift1);

            // Worker 2: Shift A (Monday 08:00 - 16:00)
            BubbleShift shift2a = new BubbleShift();
            shift2a.setAssignedUser(name2);
            shift2a.setStartTime(mondayEight.toString());
            shift2a.setEndTime(mondayEight.plus(8, ChronoUnit.HOURS).toString());
            shift2a.setNotes("Monday daytime");
            shifts.add(shift2a);

            // Worker 2: Shift B (Monday 24:00 - Tuesday 08:00) => Rest period is only 8 hours (16:00 - 24:00)
            BubbleShift shift2b = new BubbleShift();
            shift2b.setAssignedUser(name2);
            shift2b.setStartTime(mondayEight.plus(16, ChronoUnit.HOURS).toString()); // Monday 24:00 (16h after Monday 08:00)
            shift2b.setEndTime(mondayEight.plus(24, ChronoUnit.HOURS).toString());   // Tuesday 08:00
            shift2b.setNotes("Monday night shift");
            shifts.add(shift2b);

        } else {
            logs.add("Generating simulated fully compliant schedule:");
            logs.add("- " + name1 + ": Monday and Wednesday Day Shifts (8h each).");
            logs.add("- " + name2 + ": Tuesday and Thursday Night Shifts (8h each).");

            // Worker 1: Shift A (Monday 08:00 - 16:00)
            BubbleShift shift1a = new BubbleShift();
            shift1a.setAssignedUser(name1);
            shift1a.setStartTime(mondayEight.toString());
            shift1a.setEndTime(mondayEight.plus(8, ChronoUnit.HOURS).toString());
            shift1a.setNotes("Monday shift");
            shifts.add(shift1a);

            // Worker 1: Shift B (Wednesday 08:00 - 16:00) => Rest period is 40 hours (fully compliant)
            BubbleShift shift1b = new BubbleShift();
            shift1b.setAssignedUser(name1);
            shift1b.setStartTime(mondayEight.plus(48, ChronoUnit.HOURS).toString()); // Wed 08:00
            shift1b.setEndTime(mondayEight.plus(56, ChronoUnit.HOURS).toString());
            shift1b.setNotes("Wednesday shift");
            shifts.add(shift1b);

            // Worker 2: Shift A (Tuesday 22:00 - Wednesday 06:00) => Night Shift
            BubbleShift shift2a = new BubbleShift();
            shift2a.setAssignedUser(name2);
            shift2a.setStartTime(mondayEight.plus(38, ChronoUnit.HOURS).toString()); // Tue 22:00
            shift2a.setEndTime(mondayEight.plus(46, ChronoUnit.HOURS).toString());   // Wed 06:00
            shift2a.setNotes("Tuesday Night shift");
            shifts.add(shift2a);

            // Worker 2: Shift B (Thursday 22:00 - Friday 06:00) => Rest period is 40 hours (fully compliant)
            BubbleShift shift2b = new BubbleShift();
            shift2b.setAssignedUser(name2);
            shift2b.setStartTime(mondayEight.plus(86, ChronoUnit.HOURS).toString()); // Thu 22:00
            shift2b.setEndTime(mondayEight.plus(94, ChronoUnit.HOURS).toString());
            shift2b.setNotes("Thursday Night shift");
            shifts.add(shift2b);
        }

        return shifts;
    }

    private void enrichShifts(List<BubbleShift> shifts, List<BubbleUser> workers, List<BubbleWageRate> wages, String companyId) {
        if (shifts == null || shifts.isEmpty()) {
            return;
        }

        // Map worker name/id to their company name from WageRate
        Map<String, String> workerToCompany = new HashMap<>();
        
        // Build map workerName -> workerId
        Map<String, String> nameToId = new HashMap<>();
        for (BubbleUser w : workers) {
            if (w.getName() != null && w.getId() != null) {
                nameToId.put(w.getName(), w.getId());
                nameToId.put(w.getId(), w.getId()); // Map ID to ID to support direct ID usage
            }
        }

        // Look up companies from wage rates
        for (BubbleWageRate rate : wages) {
            String rateUser = rate.getUser();
            if (rateUser == null) continue;
            
            workerToCompany.put(rateUser, rate.getCompany());
            
            if (nameToId.containsKey(rateUser)) {
                workerToCompany.put(nameToId.get(rateUser), rate.getCompany());
            }
            
            for (Map.Entry<String, String> entry : nameToId.entrySet()) {
                if (entry.getValue().equals(rateUser)) {
                    workerToCompany.put(entry.getKey(), rate.getCompany());
                }
            }
        }

        ZoneId estoniaZone = ZoneId.of("Europe/Tallinn");

        for (BubbleShift shift : shifts) {
            String user = shift.getAssignedUser();
            
            // 0. Set Status to Draft by default
            shift.setStatus("Draft");

            // Resolve assigned user to Bubble Unique ID
            if (user != null) {
                String userId = nameToId.get(user);
                if (userId != null) {
                    shift.setAssignedUser(userId);
                    user = userId;
                }
            }
            
            // 1. Set Company
            if (companyId != null && !companyId.trim().isEmpty()) {
                shift.setAssignedCompany(companyId);
            } else if (user != null) {
                String company = workerToCompany.get(user);
                if (company != null) {
                    shift.setAssignedCompany(company);
                }
            }

            // 2. Set Type dynamically (Estonian law standard: 22:00 to 06:00 is Night shift)
            if (shift.getStartTime() != null && shift.getEndTime() != null) {
                try {
                    Instant startInstant = Instant.parse(shift.getStartTime());
                    Instant endInstant = Instant.parse(shift.getEndTime());
                    ZonedDateTime startEst = ZonedDateTime.ofInstant(startInstant, estoniaZone);
                    ZonedDateTime endEst = ZonedDateTime.ofInstant(endInstant, estoniaZone);

                    boolean isNight = false;
                    ZonedDateTime current = startEst;
                    while (current.isBefore(endEst)) {
                        int hour = current.getHour();
                        if (hour >= 22 || hour < 6) {
                            isNight = true;
                            break;
                        }
                        current = current.plusMinutes(15);
                    }
                    shift.setType(isNight ? "Night" : "Regular");
                } catch (Exception e) {
                    shift.setType("Regular");
                }
            } else {
                shift.setType("Regular");
            }
        }
    }

    // JSON DTO Classes mapping Gemini REST API Candidate Response envelopes
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiResponse {
        private List<Candidate> candidates;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Candidate {
            private Content content;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Content {
            private List<Part> parts;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Part {
            private String text;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiOutput {
        private List<GeminiShift> proposedShifts;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiShift {
        private String assignedUser;
        private String startTime;
        private String endTime;
        private String notes;
    }
}
