package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.dto.ScheduleGenerateResponse;
import com.comforthub.backoffice.dto.ShiftResponseDto;
import com.comforthub.backoffice.dto.ValidationReport;
import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.model.BubbleStore;
import com.comforthub.backoffice.model.BubbleUser;
import com.comforthub.backoffice.model.BubbleWageRate;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScheduleOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleOrchestrationService.class);

    private final BubbleClient bubbleClient;
    private final DeterministicValidator validator;
    private final ObjectMapper objectMapper;
    private final GeminiPromptBuilder promptBuilder;
    private final RestTemplate restTemplate;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.model}")
    private String geminiModel;

    public ScheduleOrchestrationService(BubbleClient bubbleClient,
                                        DeterministicValidator validator,
                                        ObjectMapper objectMapper,
                                        GeminiPromptBuilder promptBuilder) {
        this.bubbleClient = bubbleClient;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.restTemplate = new RestTemplate();
    }

    public ScheduleGenerateResponse generateSchedule(String userPrompt, String month, String company,
                                                     List<String> workerFilter, Integer bufferBeforeMinutes,
                                                     Integer bufferAfterMinutes) {
        List<String> logs = new ArrayList<>();
        logs.add("Initiating schedule generation...");

        // 1. Fetch data from Bubble
        logs.add("Fetching worker, wage, store and availability context from Bubble...");
        List<BubbleUser> workers = bubbleClient.fetchUsers();
        List<BubbleWageRate> wages = bubbleClient.fetchWageRates();
        List<BubbleStore> stores = bubbleClient.fetchStores();
        List<BubbleAvailability> availability = bubbleClient.fetchAvailability();

        // Filter workers by company if specified
        if (company != null && !company.isBlank()) {
            List<String> companyWorkerIds = wages.stream()
                    .filter(w -> company.equals(w.getCompany()))
                    .map(BubbleWageRate::getUser)
                    .collect(Collectors.toList());
            workers = workers.stream()
                    .filter(w -> companyWorkerIds.contains(w.getId()) || companyWorkerIds.contains(w.getName()))
                    .collect(Collectors.toList());
            logs.add("Filtered to " + workers.size() + " workers for company: " + company);
        }

        // Filter workers by explicit list if provided
        if (workerFilter != null && !workerFilter.isEmpty()) {
            final List<BubbleUser> allWorkers = workers;
            workers = allWorkers.stream()
                    .filter(w -> workerFilter.contains(w.getId()) || workerFilter.contains(w.getName()))
                    .collect(Collectors.toList());
            logs.add("Filtered to " + workers.size() + " workers from explicit list.");
        }

        // Backend #4: drop inactive workers from the candidate pool before they reach the prompt
        // or the validator. `active == false` is explicit; null (field never set on legacy Bubble
        // rows) is treated as active so we fail open rather than silently excluding everyone.
        workers = workers.stream()
                .filter(w -> !Boolean.FALSE.equals(w.getActive()))
                .collect(Collectors.toList());
        logs.add("Filtered to " + workers.size() + " active workers.");

        // Backend #4: trim wage rates and stores down to what the remaining workers/company
        // actually need before formatting the prompt (unused worker metadata / inactive stores).
        Set<String> workerIds = workers.stream().map(BubbleUser::getId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> workerNames = workers.stream().map(BubbleUser::getName).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        List<BubbleWageRate> relevantWages = wages.stream()
                .filter(w -> workerIds.contains(w.getUser()) || workerNames.contains(w.getUser()))
                .collect(Collectors.toList());
        List<BubbleStore> activeStores = stores.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .filter(s -> company == null || company.isBlank() || company.equals(s.getCompany()))
                .collect(Collectors.toList());
        logs.add("Filtered to " + relevantWages.size() + " relevant wage rates and "
                + activeStores.size() + " active stores.");

        // Backend #2: index availability records by the thing (worker/user or store) they're
        // attached to, so both the prompt builder and the deterministic validator can look a
        // worker's or store's constraints up by id. Workers with no availability record on file
        // are left unconstrained (see GeminiPromptBuilder#formatAvailability javadoc).
        Map<String, BubbleAvailability> workerAvailabilityById = availability.stream()
                .filter(a -> isWorkerOrUserThing(a.getThingType()) && a.getThingId() != null
                        && workerIds.contains(a.getThingId()))
                .collect(Collectors.toMap(BubbleAvailability::getThingId, a -> a, (a, b) -> a));
        Map<String, BubbleAvailability> storeAvailabilityById = availability.stream()
                .filter(a -> isStoreThing(a.getThingType()) && a.getThingId() != null)
                .collect(Collectors.toMap(BubbleAvailability::getThingId, a -> a, (a, b) -> a));
        logs.add("Resolved availability for " + workerAvailabilityById.size() + " of " + workers.size()
                + " workers (others treated as unrestricted).");

        // Build enriched prompt with context
        String enrichedPrompt = userPrompt != null ? userPrompt : "";
        if (month != null && !month.isBlank()) {
            enrichedPrompt = "Month: " + month + ". " + enrichedPrompt;
        }
        if (bufferBeforeMinutes != null && bufferBeforeMinutes > 0) {
            enrichedPrompt += " Schedule workers " + bufferBeforeMinutes + " minutes before store opening.";
        }
        if (bufferAfterMinutes != null && bufferAfterMinutes > 0) {
            enrichedPrompt += " Schedule workers " + bufferAfterMinutes + " minutes after store closing.";
        }

        List<BubbleShift> proposedShifts;

        // Check if we should run in live Gemini mode or fallback to simulation
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.equals("default-gemini-key")) {
            logs.add("[SIMULATION MODE] Gemini API Key is not set. Running simulated schedule generation...");
            proposedShifts = runSimulatedLLM(enrichedPrompt, workers, logs);
        } else {
            logs.add("[LIVE MODE] Running real Gemini model generation via API...");
            proposedShifts = runRealGemini(enrichedPrompt, workers, relevantWages, activeStores,
                    workerAvailabilityById, storeAvailabilityById, logs);
        }

        // Enrich shifts with company and type before validation
        enrichShifts(proposedShifts, workers, relevantWages);

        // 2. Run Deterministic Validation (Estonian labor laws, constraints & per-worker availability)
        logs.add("Sending proposed shifts to Deterministic Validator Gatekeeper...");
        ValidationReport report = validator.validate(proposedShifts, workers, workerAvailabilityById);

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
                                            List<BubbleStore> stores, Map<String, BubbleAvailability> workerAvailabilityById,
                                            Map<String, BubbleAvailability> storeAvailabilityById, List<String> logs) {
        try {
            // Get current date context in Estonia
            ZoneId estoniaZone = ZoneId.of("Europe/Tallinn");
            ZonedDateTime now = ZonedDateTime.now(estoniaZone);
            ZonedDateTime nextMonday = now.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
            String todayStr = now.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd"));
            String nextMondayStr = nextMonday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Build the compact prompt (backend #4: trimmed context; backend #2: availability
            // constraints folded into each worker/store line) via the dedicated prompt builder.
            Map<String, BigDecimal> rateById = promptBuilder.buildRateLookup(wages, workers);
            String workerContext = promptBuilder.buildWorkerContext(workers, rateById, workerAvailabilityById);
            String storeContext = promptBuilder.buildStoreContext(stores, storeAvailabilityById);
            String fullPrompt = promptBuilder.assemblePrompt(todayStr, nextMondayStr, workerContext, storeContext, userPrompt);

            logs.add("Assembled prompt context with " + workers.size() + " workers and "
                    + (stores != null ? stores.size() : 0) + " stores.");

            // Build HTTP Request payload
            Map<String, Object> requestBody = new HashMap<>();

            Map<String, Object> part = new HashMap<>();
            part.put("text", fullPrompt);

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
        return runSimulatedLLM(userPrompt, workers, logs);
    }

    private List<BubbleShift> runSimulatedLLM(String prompt, List<BubbleUser> workers, List<String> logs) {
        List<BubbleShift> shifts = new ArrayList<>();

        // Define standard week starting Monday in the future (e.g. 2026-06-29)
        Instant mondayEight = Instant.parse("2026-06-29T08:00:00Z");

        // Pick names from workers, fallback to defaults if list is empty
        String name1 = !workers.isEmpty() ? workers.get(0).getName() : "Tom";
        String name2 = workers.size() > 1 ? workers.get(1).getName() : "Lily";

        if (prompt != null && (prompt.toLowerCase().contains("fail") || prompt.toLowerCase().contains("violation"))) {
            logs.add("Prompt requested violations. Generating simulated schedule with Estonian labor law violations:");
            logs.add("- Violation 1: " + name1 + " will have a shift of 13 hours (limit is 12h).");
            logs.add("- Violation 2: " + name2 + " will have only 8 hours of rest between consecutive shifts (limit is 11h).");

            BubbleShift shift1 = new BubbleShift();
            shift1.setAssignedUser(name1);
            shift1.setStartTime(mondayEight.toString());
            shift1.setEndTime(mondayEight.plus(13, ChronoUnit.HOURS).toString());
            shift1.setNotes("Long day shift");
            shifts.add(shift1);

            BubbleShift shift2a = new BubbleShift();
            shift2a.setAssignedUser(name2);
            shift2a.setStartTime(mondayEight.toString());
            shift2a.setEndTime(mondayEight.plus(8, ChronoUnit.HOURS).toString());
            shift2a.setNotes("Monday daytime");
            shifts.add(shift2a);

            BubbleShift shift2b = new BubbleShift();
            shift2b.setAssignedUser(name2);
            shift2b.setStartTime(mondayEight.plus(16, ChronoUnit.HOURS).toString());
            shift2b.setEndTime(mondayEight.plus(24, ChronoUnit.HOURS).toString());
            shift2b.setNotes("Monday night shift");
            shifts.add(shift2b);

        } else {
            logs.add("Generating simulated fully compliant schedule:");
            logs.add("- " + name1 + ": Monday and Wednesday Day Shifts (8h each).");
            logs.add("- " + name2 + ": Tuesday and Thursday Night Shifts (8h each).");

            BubbleShift shift1a = new BubbleShift();
            shift1a.setAssignedUser(name1);
            shift1a.setStartTime(mondayEight.toString());
            shift1a.setEndTime(mondayEight.plus(8, ChronoUnit.HOURS).toString());
            shift1a.setNotes("Monday shift");
            shifts.add(shift1a);

            BubbleShift shift1b = new BubbleShift();
            shift1b.setAssignedUser(name1);
            shift1b.setStartTime(mondayEight.plus(48, ChronoUnit.HOURS).toString());
            shift1b.setEndTime(mondayEight.plus(56, ChronoUnit.HOURS).toString());
            shift1b.setNotes("Wednesday shift");
            shifts.add(shift1b);

            BubbleShift shift2a = new BubbleShift();
            shift2a.setAssignedUser(name2);
            shift2a.setStartTime(mondayEight.plus(38, ChronoUnit.HOURS).toString());
            shift2a.setEndTime(mondayEight.plus(46, ChronoUnit.HOURS).toString());
            shift2a.setNotes("Tuesday Night shift");
            shifts.add(shift2a);

            BubbleShift shift2b = new BubbleShift();
            shift2b.setAssignedUser(name2);
            shift2b.setStartTime(mondayEight.plus(86, ChronoUnit.HOURS).toString());
            shift2b.setEndTime(mondayEight.plus(94, ChronoUnit.HOURS).toString());
            shift2b.setNotes("Thursday Night shift");
            shifts.add(shift2b);
        }

        return shifts;
    }

    private void enrichShifts(List<BubbleShift> shifts, List<BubbleUser> workers, List<BubbleWageRate> wages) {
        if (shifts == null || shifts.isEmpty()) {
            return;
        }

        Map<String, String> workerToCompany = new HashMap<>();
        Map<String, String> nameToId = new HashMap<>();
        for (BubbleUser w : workers) {
            if (w.getName() != null && w.getId() != null) {
                nameToId.put(w.getName(), w.getId());
            }
        }

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

            if (user != null) {
                String company = workerToCompany.get(user);
                if (company != null) {
                    shift.setAssignedCompany(company);
                }
            }

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

    /**
     * Whether a {@code bubble_availability} {@code thingType} refers to a worker/user (as opposed to
     * a store). Mirrors {@code AvailabilityController#ownsThing}'s case-insensitive prefix match —
     * the {@code things} option set uses {@code "Worker"} / {@code "User"} / {@code "Store"}.
     */
    static boolean isWorkerOrUserThing(String thingType) {
        if (thingType == null) {
            return false;
        }
        String t = thingType.trim().toLowerCase();
        return t.startsWith("worker") || t.startsWith("user");
    }

    /** Whether a {@code bubble_availability} {@code thingType} refers to a store. */
    static boolean isStoreThing(String thingType) {
        return thingType != null && thingType.trim().toLowerCase().startsWith("store");
    }

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
