package com.comforthub.backoffice.service;

import com.comforthub.backoffice.dto.ValidationIssue;
import com.comforthub.backoffice.dto.ValidationReport;
import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.model.BubbleUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class DeterministicValidator {

    private static final Logger log = LoggerFactory.getLogger(DeterministicValidator.class);

    private static final java.time.ZoneId ESTONIA_ZONE = java.time.ZoneId.of("Europe/Tallinn");
    private static final Set<String> WEEKEND_DAYS = Set.of("Saturday", "Sunday");

    /** Backward-compatible overload: no availability data, so the availability checks are skipped entirely. */
    public ValidationReport validate(List<BubbleShift> proposedShifts, List<BubbleUser> workers) {
        return validate(proposedShifts, workers, Collections.emptyMap());
    }

    /**
     * Validates proposed shifts against Estonian labor-law rules (max duration, min rest, weekly
     * hours cap) plus, when available, each worker's individual {@code bubble_availability} record
     * (backend #2) — allowed days and workday/weekend hour windows.
     *
     * @param workerAvailabilityById availability records keyed by worker/user Bubble id (see
     *                              {@code ScheduleOrchestrationService#generateSchedule}). A worker
     *                              with no entry here is treated as <b>unconstrained</b> — the
     *                              documented fallback, since availability rows are opt-in and most
     *                              workers won't have one yet.
     */
    public ValidationReport validate(List<BubbleShift> proposedShifts, List<BubbleUser> workers,
                                     Map<String, BubbleAvailability> workerAvailabilityById) {
        ValidationReport report = new ValidationReport();

        if (proposedShifts == null || proposedShifts.isEmpty()) {
            return report;
        }

        Map<String, BubbleAvailability> availabilityById = workerAvailabilityById == null
                ? Collections.emptyMap() : workerAvailabilityById;

        Map<String, BubbleUser> workerMap = new HashMap<>();
        for (BubbleUser worker : workers) {
            if (worker.getId() != null) workerMap.put(worker.getId(), worker);
            if (worker.getName() != null) workerMap.put(worker.getName(), worker);
        }

        Map<String, List<ParsedShift>> shiftsByWorker = new HashMap<>();

        for (BubbleShift shift : proposedShifts) {
            String user = shift.getAssignedUser();
            if (user == null || user.trim().isEmpty()) {
                report.addIssue(new ValidationIssue(
                        ValidationIssue.Severity.ERROR, "UNASSIGNED", "Unknown",
                        "Shift has no assigned worker: " + shift.getStartTime() + " to " + shift.getEndTime()
                ));
                continue;
            }

            ParsedShift parsed = parseShift(shift, report);
            if (parsed == null) continue;

            long durationMinutes = Duration.between(parsed.start, parsed.end).toMinutes();
            if (durationMinutes > 720) {
                report.addIssue(new ValidationIssue(
                        ValidationIssue.Severity.ERROR, "MAX_DURATION", user,
                        String.format("Shift duration exceeds 12 hours: %.1f hours (%s - %s)",
                                durationMinutes / 60.0, shift.getStartTime(), shift.getEndTime())
                ));
            } else if (durationMinutes <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationIssue.Severity.ERROR, "INVALID_TIME", user,
                        "Shift end time must be after start time: " + shift.getStartTime() + " to " + shift.getEndTime()
                ));
            }

            checkAvailability(shift, user, parsed, workerMap, availabilityById, report);

            shiftsByWorker.computeIfAbsent(user, k -> new ArrayList<>()).add(parsed);
        }

        for (Map.Entry<String, List<ParsedShift>> entry : shiftsByWorker.entrySet()) {
            String user = entry.getKey();
            List<ParsedShift> userShifts = entry.getValue();
            userShifts.sort(Comparator.comparing(ps -> ps.start));

            long totalMinutes = 0;
            for (int i = 0; i < userShifts.size(); i++) {
                ParsedShift current = userShifts.get(i);
                totalMinutes += Duration.between(current.start, current.end).toMinutes();

                if (i < userShifts.size() - 1) {
                    ParsedShift next = userShifts.get(i + 1);

                    if (next.start.isBefore(current.end)) {
                        report.addIssue(new ValidationIssue(
                                ValidationIssue.Severity.ERROR, "OVERLAP", user,
                                String.format("Shifts overlap: [%s - %s] and [%s - %s]",
                                        current.raw.getStartTime(), current.raw.getEndTime(),
                                        next.raw.getStartTime(), next.raw.getEndTime())
                        ));
                        continue;
                    }

                    long restMinutes = Duration.between(current.end, next.start).toMinutes();
                    if (restMinutes < 660) {
                        report.addIssue(new ValidationIssue(
                                ValidationIssue.Severity.ERROR, "MIN_REST", user,
                                String.format("Rest period between shifts is only %.1f hours (required >= 11 hours). Gap between [%s] and [%s]",
                                        restMinutes / 60.0, current.raw.getEndTime(), next.raw.getStartTime())
                        ));
                    }
                }
            }

            BubbleUser worker = workerMap.get(user);
            if (worker != null && worker.getMaxHours() != null) {
                double totalHours = totalMinutes / 60.0;
                if (totalHours > worker.getMaxHours()) {
                    report.addIssue(new ValidationIssue(
                            ValidationIssue.Severity.ERROR, "WEEKLY_HOURS", user,
                            String.format("Total weekly hours (%.1f) exceeds worker's maximum agreed limit of %d hours.",
                                    totalHours, worker.getMaxHours())
                    ));
                }
            }
        }

        return report;
    }

    /**
     * Backend #2: rejects a shift that falls on a day the worker is not available, or outside their
     * workday/weekend hour window for that day. A worker with no availability record on file is
     * skipped entirely (unconstrained fallback — see class javadoc on the 3-arg {@code validate}).
     *
     * <p><b>Known simplification:</b> like the existing night-shift classification in
     * {@code ScheduleOrchestrationService#enrichShifts}, this does not split a shift that crosses
     * midnight into its constituent days — the hour-window check uses the shift's start day only.
     * Overnight shifts against a narrow availability window may therefore not be flagged precisely;
     * flagging this as a follow-up rather than adding day-splitting logic here.
     */
    private void checkAvailability(BubbleShift shift, String user, ParsedShift parsed,
                                   Map<String, BubbleUser> workerMap,
                                   Map<String, BubbleAvailability> availabilityById,
                                   ValidationReport report) {
        if (availabilityById.isEmpty()) {
            return;
        }
        BubbleUser worker = workerMap.get(user);
        String workerId = (worker != null && worker.getId() != null) ? worker.getId() : user;
        BubbleAvailability availability = availabilityById.get(workerId);
        if (availability == null) {
            return;
        }

        ZonedDateTime startLocal = parsed.start.atZone(ESTONIA_ZONE);
        ZonedDateTime endLocal = parsed.end.atZone(ESTONIA_ZONE);
        String dayName = startLocal.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        List<String> allowedDays = availability.getAvailableDays();
        if (allowedDays != null && !allowedDays.isEmpty() && !allowedDays.contains(dayName)) {
            report.addIssue(new ValidationIssue(
                    ValidationIssue.Severity.ERROR, "AVAILABILITY_DAY", user,
                    String.format("Worker is not available on %s (allowed days: %s). Shift: %s - %s",
                            dayName, String.join(", ", allowedDays), shift.getStartTime(), shift.getEndTime())
            ));
            return; // day mismatch already flags the shift; skip the hour check to avoid double-noise
        }

        boolean weekend = WEEKEND_DAYS.contains(dayName);
        Integer windowStart = weekend ? availability.getWeekendStartHour() : availability.getWorkdayStartHour();
        Integer windowEnd = weekend ? availability.getWeekendEndHour() : availability.getWorkdayEndHour();
        if (windowStart == null || windowEnd == null) {
            return; // no hour window on file for this day type -> unconstrained fallback
        }

        int startHour = startLocal.getHour();
        // A shift ending exactly on the hour doesn't "work" that hour; only round up when there's a remainder.
        int endHour = endLocal.getHour() + (endLocal.getMinute() > 0 || endLocal.getSecond() > 0 ? 1 : 0);
        if (startHour < windowStart || endHour > windowEnd) {
            report.addIssue(new ValidationIssue(
                    ValidationIssue.Severity.ERROR, "AVAILABILITY_HOURS", user,
                    String.format("Shift %s - %s falls outside worker's %s hours (%02d:00-%02d:00).",
                            shift.getStartTime(), shift.getEndTime(), weekend ? "weekend" : "workday",
                            windowStart, windowEnd)
            ));
        }
    }

    private ParsedShift parseShift(BubbleShift shift, ValidationReport report) {
        try {
            Instant start = Instant.parse(shift.getStartTime());
            Instant end = Instant.parse(shift.getEndTime());
            return new ParsedShift(start, end, shift);
        } catch (Exception e) {
            report.addIssue(new ValidationIssue(
                    ValidationIssue.Severity.ERROR, "PARSE_ERROR", shift.getAssignedUser(),
                    "Invalid date format: start=" + shift.getStartTime() + ", end=" + shift.getEndTime()
            ));
            return null;
        }
    }

    private static class ParsedShift {
        final Instant start;
        final Instant end;
        final BubbleShift raw;

        ParsedShift(Instant start, Instant end, BubbleShift raw) {
            this.start = start;
            this.end = end;
            this.raw = raw;
        }
    }
}
