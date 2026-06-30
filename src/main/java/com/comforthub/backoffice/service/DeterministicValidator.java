package com.comforthub.backoffice.service;

import com.comforthub.backoffice.dto.ValidationIssue;
import com.comforthub.backoffice.dto.ValidationReport;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.model.BubbleUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class DeterministicValidator {

    private static final Logger log = LoggerFactory.getLogger(DeterministicValidator.class);

    public ValidationReport validate(List<BubbleShift> proposedShifts, List<BubbleUser> workers) {
        ValidationReport report = new ValidationReport();

        if (proposedShifts == null || proposedShifts.isEmpty()) {
            return report;
        }

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
