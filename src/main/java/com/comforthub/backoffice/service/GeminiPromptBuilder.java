package com.comforthub.backoffice.service;

import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleStore;
import com.comforthub.backoffice.model.BubbleUser;
import com.comforthub.backoffice.model.BubbleWageRate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the Gemini prompt payload (system prompt + worker/store context) for
 * {@link ScheduleOrchestrationService}. Split out of the orchestrator (backend
 * issue #4) so the compact-context formatting can be unit tested without
 * standing up the Gemini HTTP call, and so token-heavy formatting lives in one
 * place.
 *
 * <p><b>Backend #4 (token/latency optimization):</b> the previous prompt
 * serialized a labelled sentence per worker ({@code "- ID: %s, Name: %s, Role:
 * %s, Max Weekly Hours: %s"}) and a second, fully redundant "Wage Rates"
 * section that repeated the worker identifier again. This builder emits one
 * compact pipe-delimited line per worker/store instead (id|name|role|maxH|
 * rate|availability), with the field legend documented once in the system
 * prompt rather than per line. Callers are expected to have already stripped
 * inactive workers/stores and unrelated wage records (see
 * {@link ScheduleOrchestrationService#generateSchedule}) — this class only
 * formats whatever it is given.
 *
 * <p><b>Backend #2 (worker-specific availability):</b> {@link
 * #formatAvailability(BubbleAvailability)} renders a worker's or store's
 * {@code bubble_availability} record (see {@code AvailabilityBubbleMapper},
 * merged PR #97/#104) as a compact constraint string — allowed days, explicit
 * days off (the complement of the allowed days), and workday/weekend hour
 * windows — or {@code "unrestricted"} when no record is on file. "No record on
 * file" is treated as unconstrained by design (documented fallback): Bubble
 * availability rows are opt-in (lazy-created via {@code AvailabilityController
 * PUT /api/availability/{id}}), so most workers will not have one yet, and
 * failing generation for them would defeat the point of the feature.
 */
@Component
public class GeminiPromptBuilder {

    static final List<String> ALL_DAYS = List.of(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an expert workforce scheduling agent. Today's date is %s. Assign workers to shifts for the week starting Monday, %s.
            Generate shifts that cover demand based on worker availability, preferences, and wage rates.
            Output a JSON object with an array 'proposedShifts'; each item has:
            - assignedUser: the exact worker id or name from the Workers context below.
            - startTime / endTime: ISO-8601 UTC (e.g. %sT08:00:00Z).
            - notes: a brief comment.

            Compliance rules (Estonia):
            1. No shift longer than 12 hours.
            2. At least 11 hours of continuous rest between shifts for the same worker.
            3. Shifts between 22:00 and 06:00 are night shifts; minimize them where possible.

            Context below uses compact pipe-delimited fields, one entity per line; the header on each block names the fields in order.
            A worker/store 'availability' field is either 'unrestricted' (no specific limits; other constraints still apply) or a ';'-joined list of:
            days=<allowed weekdays, abbreviated Mon/Tue/.../Sun>, off=<explicit days off, same abbreviation>, wd=<HH-HH allowed hour window on workdays Mon-Fri>, we=<HH-HH allowed hour window on weekends Sat-Sun>.
            You MUST NOT assign a worker a shift on a day in their 'off=' list or absent from their 'days=' list, and MUST NOT schedule outside their 'wd='/'we=' hour window for that day. Store 'hours' apply the same wd=/we= constraint store-wide.
            """;

    /** The static rules/instructions portion of the prompt (date-stamped, no context data yet). */
    public String buildSystemPrompt(String todayStr, String nextMondayStr) {
        return SYSTEM_PROMPT_TEMPLATE.formatted(todayStr, nextMondayStr, nextMondayStr);
    }

    /**
     * Compact worker context block: one {@code id|name|role|maxWeeklyHours|hourlyRate|availability}
     * line per worker. Callers should pass an already-trimmed {@code workers} list (active only,
     * company/explicit-filter applied) — see backend #4.
     */
    public String buildWorkerContext(List<BubbleUser> workers, Map<String, BigDecimal> rateById,
                                     Map<String, BubbleAvailability> workerAvailabilityById) {
        if (workers == null || workers.isEmpty()) {
            return "Workers: none.";
        }
        Map<String, BubbleAvailability> availabilityById = workerAvailabilityById == null
                ? Map.of() : workerAvailabilityById;
        Map<String, BigDecimal> rates = rateById == null ? Map.of() : rateById;

        StringBuilder sb = new StringBuilder();
        sb.append("Workers - id|name|role|maxWeeklyHours|hourlyRate|availability (\"-\" = not set):\n");
        for (BubbleUser w : workers) {
            BigDecimal rate = w.getId() != null ? rates.get(w.getId()) : null;
            if (rate == null && w.getName() != null) {
                rate = rates.get(w.getName());
            }
            BubbleAvailability availability = w.getId() != null ? availabilityById.get(w.getId()) : null;

            sb.append("- ")
                    .append(nullToDash(w.getId())).append('|')
                    .append(nullToDash(w.getName())).append('|')
                    .append(nullToDash(w.getRole())).append('|')
                    .append(w.getMaxHours() != null ? w.getMaxHours() : "-").append('|')
                    .append(rate != null ? rate.stripTrailingZeros().toPlainString() : "-").append('|')
                    .append(formatAvailability(availability))
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * Compact store context block: one {@code name|company|hours} line per store. Callers should
     * pass an already-trimmed {@code stores} list (non-deleted, company-scoped) — see backend #4.
     * Returns {@code ""} when there are no stores, so the orchestrator can omit the block entirely.
     */
    public String buildStoreContext(List<BubbleStore> stores, Map<String, BubbleAvailability> storeAvailabilityById) {
        if (stores == null || stores.isEmpty()) {
            return "";
        }
        Map<String, BubbleAvailability> availabilityById = storeAvailabilityById == null
                ? Map.of() : storeAvailabilityById;

        StringBuilder sb = new StringBuilder();
        sb.append("Stores - name|company|hours (\"-\" = not set):\n");
        for (BubbleStore s : stores) {
            BubbleAvailability availability = s.getId() != null ? availabilityById.get(s.getId()) : null;
            sb.append("- ")
                    .append(nullToDash(s.getName())).append('|')
                    .append(nullToDash(s.getCompany())).append('|')
                    .append(formatAvailability(availability))
                    .append('\n');
        }
        return sb.toString();
    }

    /** Concatenates the system prompt, worker/store context, and the user's free-text guidelines. */
    public String assemblePrompt(String todayStr, String nextMondayStr, String workerContext,
                                 String storeContext, String userPrompt) {
        StringBuilder sb = new StringBuilder(buildSystemPrompt(todayStr, nextMondayStr));
        sb.append('\n').append(workerContext);
        if (storeContext != null && !storeContext.isBlank()) {
            sb.append('\n').append(storeContext);
        }
        sb.append("\nUser Custom Guidelines:\n").append(userPrompt == null ? "" : userPrompt);
        return sb.toString();
    }

    /**
     * Renders one {@code bubble_availability} record as a compact constraint string, or
     * {@code "unrestricted"} when {@code availability} is {@code null} (no record on file — the
     * documented fallback for backend #2) or has no usable fields set.
     */
    public String formatAvailability(BubbleAvailability availability) {
        if (availability == null) {
            return "unrestricted";
        }
        List<String> parts = new ArrayList<>();
        List<String> days = availability.getAvailableDays();
        if (days != null && !days.isEmpty()) {
            parts.add("days=" + abbreviateDays(days));
            List<String> off = ALL_DAYS.stream().filter(d -> !days.contains(d)).collect(Collectors.toList());
            if (!off.isEmpty()) {
                parts.add("off=" + abbreviateDays(off));
            }
        }
        if (availability.getWorkdayStartHour() != null && availability.getWorkdayEndHour() != null) {
            parts.add(String.format("wd=%02d-%02d", availability.getWorkdayStartHour(), availability.getWorkdayEndHour()));
        }
        if (availability.getWeekendStartHour() != null && availability.getWeekendEndHour() != null) {
            parts.add(String.format("we=%02d-%02d", availability.getWeekendStartHour(), availability.getWeekendEndHour()));
        }
        return parts.isEmpty() ? "unrestricted" : String.join(";", parts);
    }

    /**
     * Resolves an hourly rate per worker, keyed by both worker id and worker name so lookups work
     * regardless of which one a {@link BubbleWageRate#getUser()} value happens to reference (mirrors
     * the id/name cross-reference {@link ScheduleOrchestrationService#enrichShifts} already does for
     * company). Callers should pass an already-trimmed {@code wages} list (only the remaining
     * workers' rates) — see backend #4.
     */
    public Map<String, BigDecimal> buildRateLookup(List<BubbleWageRate> wages, List<BubbleUser> workers) {
        Map<String, String> nameToId = new HashMap<>();
        if (workers != null) {
            for (BubbleUser w : workers) {
                if (w.getName() != null && w.getId() != null) {
                    nameToId.put(w.getName(), w.getId());
                }
            }
        }
        Map<String, BigDecimal> rateById = new HashMap<>();
        if (wages == null) {
            return rateById;
        }
        for (BubbleWageRate rate : wages) {
            String rateUser = rate.getUser();
            if (rateUser == null || rate.getRate() == null) {
                continue;
            }
            rateById.putIfAbsent(rateUser, rate.getRate());
            String resolvedId = nameToId.get(rateUser);
            if (resolvedId != null) {
                rateById.putIfAbsent(resolvedId, rate.getRate());
            }
        }
        return rateById;
    }

    private static String abbreviateDays(List<String> days) {
        return days.stream().map(GeminiPromptBuilder::abbreviate).collect(Collectors.joining(","));
    }

    /** First 3 characters of a weekday option-set value (e.g. "Tuesday" -> "Tue"); passed through if shorter. */
    private static String abbreviate(String day) {
        if (day == null || day.length() < 3) {
            return day;
        }
        return day.substring(0, 3);
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
