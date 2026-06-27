package com.example.scheduler.service;

import com.example.scheduler.dto.ValidationIssue;
import com.example.scheduler.dto.ValidationReport;
import com.example.scheduler.model.BubbleShift;
import com.example.scheduler.model.BubbleUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicValidatorTest {

    private DeterministicValidator validator;
    private List<BubbleUser> workers;

    @BeforeEach
    void setUp() {
        validator = new DeterministicValidator();
        workers = new ArrayList<>();
        workers.add(new BubbleUser("worker1", "Tom", "STANDARD", 40, true));
        workers.add(new BubbleUser("worker2", "Lily", "NIGHT", 30, true));
    }

    @Test
    void testCompliantSchedule() {
        List<BubbleShift> shifts = new ArrayList<>();
        
        // Tom: Monday 08:00 - 16:00 (8 hours)
        BubbleShift s1 = new BubbleShift("s1", "Tom", "2026-06-29T08:00:00Z", "2026-06-29T16:00:00Z", "Standard morning");
        shifts.add(s1);

        // Tom: Tuesday 08:00 - 16:00 (8 hours) -> 16 hours rest gap, fully compliant
        BubbleShift s2 = new BubbleShift("s2", "Tom", "2026-06-30T08:00:00Z", "2026-06-30T16:00:00Z", "Standard morning 2");
        shifts.add(s2);

        ValidationReport report = validator.validate(shifts, workers);
        assertTrue(report.isValid(), "Schedule should be compliant");
        assertTrue(report.getIssues().isEmpty(), "Should have no issues");
    }

    @Test
    void testShiftExceeding12Hours() {
        List<BubbleShift> shifts = new ArrayList<>();
        
        // Tom: Monday 08:00 - 21:00 (13 hours) -> Violates Max Duration
        BubbleShift s1 = new BubbleShift("s1", "Tom", "2026-06-29T08:00:00Z", "2026-06-29T21:00:00Z", "Overtime shift");
        shifts.add(s1);

        ValidationReport report = validator.validate(shifts, workers);
        assertFalse(report.isValid(), "Schedule should be invalid");
        assertEquals(1, report.getIssues().size(), "Should have 1 issue");
        assertEquals("MAX_DURATION", report.getIssues().get(0).getRule());
        assertEquals(ValidationIssue.Severity.ERROR, report.getIssues().get(0).getSeverity());
    }

    @Test
    void testRestGapUnder11Hours() {
        List<BubbleShift> shifts = new ArrayList<>();
        
        // Tom: Monday 08:00 - 18:00 (10 hours)
        BubbleShift s1 = new BubbleShift("s1", "Tom", "2026-06-29T08:00:00Z", "2026-06-29T18:00:00Z", "Day Shift");
        shifts.add(s1);

        // Tom: Tuesday 02:00 - 10:00 (8 hours) -> Rest gap is only 8 hours (18:00 Monday to 02:00 Tuesday)
        BubbleShift s2 = new BubbleShift("s2", "Tom", "2026-06-30T02:00:00Z", "2026-06-30T10:00:00Z", "Early Morning Shift");
        shifts.add(s2);

        ValidationReport report = validator.validate(shifts, workers);
        assertFalse(report.isValid(), "Schedule should be invalid");
        assertTrue(report.getIssues().stream().anyMatch(issue -> "MIN_REST".equals(issue.getRule())), "Should contain MIN_REST violation");
    }

    @Test
    void testShiftsOverlap() {
        List<BubbleShift> shifts = new ArrayList<>();
        
        // Tom: Monday 08:00 - 16:00
        BubbleShift s1 = new BubbleShift("s1", "Tom", "2026-06-29T08:00:00Z", "2026-06-29T16:00:00Z", "Shift A");
        shifts.add(s1);

        // Tom: Monday 15:00 - 20:00 -> Overlaps by 1 hour
        BubbleShift s2 = new BubbleShift("s2", "Tom", "2026-06-29T15:00:00Z", "2026-06-29T20:00:00Z", "Shift B");
        shifts.add(s2);

        ValidationReport report = validator.validate(shifts, workers);
        assertFalse(report.isValid(), "Schedule should be invalid");
        assertTrue(report.getIssues().stream().anyMatch(issue -> "OVERLAP".equals(issue.getRule())), "Should contain OVERLAP violation");
    }

    @Test
    void testWeeklyHoursExceededLimit() {
        List<BubbleShift> shifts = new ArrayList<>();
        
        // Tom has a maxLimit of 40 hours. Let's assign him five 9-hour shifts = 45 hours
        for (int i = 0; i < 5; i++) {
            String day = "2026-07-0" + (1 + i);
            BubbleShift s = new BubbleShift("s" + i, "Tom", day + "T08:00:00Z", day + "T17:00:00Z", "9-hour shift");
            shifts.add(s);
        }

        ValidationReport report = validator.validate(shifts, workers);
        assertFalse(report.isValid(), "Schedule should be invalid");
        assertTrue(report.getIssues().stream().anyMatch(issue -> "WEEKLY_HOURS".equals(issue.getRule())), "Should contain WEEKLY_HOURS violation");
    }
}
