package com.example.scheduler.service;

import com.example.scheduler.dto.ValidationIssue;
import com.example.scheduler.dto.ValidationReport;
import com.example.scheduler.model.BubbleShift;
import com.example.scheduler.model.BubbleUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeterministicValidator {

    private static final Logger log = LoggerFactory.getLogger(DeterministicValidator.class);

    /**
     * Validates a list of proposed shifts against Estonian labor laws and worker constraints.
     */
    public ValidationReport validate(List<BubbleShift> proposedShifts, List<BubbleUser> workers) {
        ValidationReport report = new ValidationReport();
        
        if (proposedShifts == null || proposedShifts.isEmpty()) {
            return report;
        }

        // Map workers by their ID or name for quick lookup of limits
        Map<String, BubbleUser> workerMap = new HashMap<>();
        for (BubbleUser worker : workers) {
            if (worker.getId() != null) {
                workerMap.put(worker.getId(), worker);
            }
            if (worker.getName() != null) {
                workerMap.put(worker.getName(), worker);
            }
        }

        // Parse and pre-validate individual shifts (e.g., formatting and single shift duration)
        Map<String, List<ParsedShift>> shiftsByWorker = new HashMap<>();
        
        for (BubbleShift shift : proposedShifts) {
            String user = shift.getAssignedUser();
            if (user == null || user.trim().isEmpty()) {
                report.addIssue(new ValidationIssue(
                        ValidationIssue.Severity.ERROR,
                        "UNASSIGNED",
                        "Unknown",
                        "Shift has no assigned worker: " + shift.getStartTime() + " to " + shift.getEndTime()
                ));
                continue;
            }

            ParsedShift parsed = parseShift(shift, report);
            if (parsed == null) {
                continue; // Parsing failed and issue was logged
            }

            // Rule 1: No single worker can be assigned a shift longer than 12 hours
            long durationMinutes = Duration.between(parsed.start, parsed.end).toMinutes();
            if (durationMinutes > 720) { // 12 hours
                double hours = durationMinutes / 60.0;
                report.addIssue(new ValidationIssue(
                        ValidationIssue.Severity.ERROR,
                        "MAX_DURATION",
                        user,
                        String.format("Shift duration exceeds 12 hours: %.1f hours (%s - %s)", 
                                hours, shift.getStartTime(), shift.getEndTime())
                ));
            } else if (durationMinutes <= 0) {
                report.addIssue(new ValidationIssue(
                        ValidationIssue.Severity.ERROR,
                        "INVALID_TIME",
                        user,
                        "Shift end time must be after start time: " + shift.getStartTime() + " to " + shift.getEndTime()
                ));
            }

            shiftsByWorker.computeIfAbsent(user, k -> new ArrayList<>()).add(parsed);
        }

        // Validate multi-shift constraints per worker
        for (Map.Entry<String, List<ParsedShift>> entry : shiftsByWorker.entrySet()) {
            String user = entry.getKey();
            List<ParsedShift> userShifts = entry.getValue();

            // Sort chronologically by start time
            userShifts.sort(Comparator.comparing(ps -> ps.start));

            long totalMinutes = 0;

            for (int i = 0; i < userShifts.size(); i++) {
                ParsedShift current = userShifts.get(i);
                long currentDuration = Duration.between(current.start, current.end).toMinutes();
                totalMinutes += currentDuration;

                if (i < userShifts.size() - 1) {
                    ParsedShift next = userShifts.get(i + 1);

                    // Check for overlap
                    if (next.start.isBefore(current.end)) {
                        report.addIssue(new ValidationIssue(
                                ValidationIssue.Severity.ERROR,
                                "OVERLAP",
                                user,
                                String.format("Shifts overlap: [%s - %s] and [%s - %s]",
                                        current.raw.getStartTime(), current.raw.getEndTime(),
                                        next.raw.getStartTime(), next.raw.getEndTime())
                        ));
                        continue;
                    }

                    // Rule 2: Ensure at least 11 hours of continuous rest between shifts
                    long restMinutes = Duration.between(current.end, next.start).toMinutes();
                    if (restMinutes < 660) { // 11 hours
                        double restHours = restMinutes / 60.0;
                        report.addIssue(new ValidationIssue(
                                ValidationIssue.Severity.ERROR,
                                "MIN_REST",
                                user,
                                String.format("Rest period between shifts is only %.1f hours (required >= 11 hours). Gap between [%s] and [%s]",
                                        restHours, current.raw.getEndTime(), next.raw.getStartTime())
                        ));
                    }
                }
            }

            // Rule 3: Weekly Hour limits (max agreed weekly hours)
            BubbleUser worker = workerMap.get(user);
            if (worker != null && worker.getMaxHours() != null) {
                double totalHours = totalMinutes / 60.0;
                if (totalHours > worker.getMaxHours()) {
                    report.addIssue(new ValidationIssue(
                            ValidationIssue.Severity.ERROR,
                            "WEEKLY_HOURS",
                            user,
                            String.format("Total weekly hours (%.1f) exceeds worker's maximum agreed limit of %d hours.",
                                    totalHours, worker.getMaxHours())
                    ));
                }
            }
        }

        return report;
    }

    private ParsedShift parseShift(BubbleShift shift, ValidationReport report) {
        try {
            Instant start = Instant.parse(shift.getStartTime());
            Instant end = Instant.parse(shift.getEndTime());
            return new ParsedShift(start, end, shift);
        } catch (Exception e) {
            report.addIssue(new ValidationIssue(
                    ValidationIssue.Severity.ERROR,
                    "PARSE_ERROR",
                    shift.getAssignedUser(),
                    "Invalid date format in shift parameters: start=" + shift.getStartTime() + ", end=" + shift.getEndTime()
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
