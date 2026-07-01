package com.comforthub.backoffice.service;

import com.comforthub.backoffice.dto.ValidationIssue;
import com.comforthub.backoffice.dto.ValidationReport;
import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.model.BubbleUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DeterministicValidator}, focused on the new backend #2 availability
 * enforcement (day-of-week and workday/weekend hour window). Pre-existing Estonian labor-law rules
 * (max duration, min rest, weekly cap) are exercised incidentally via the compliant baseline case.
 *
 * <p>All shifts land on 2026-06-29 (a Monday) through 2026-07-05 (a Sunday) in Europe/Tallinn, to
 * make the day-of-week arithmetic in the validator easy to reason about in each test.
 */
class DeterministicValidatorTest {

    private final DeterministicValidator validator = new DeterministicValidator();

    private static BubbleUser worker(String id, String name) {
        BubbleUser w = new BubbleUser();
        w.setId(id);
        w.setName(name);
        return w;
    }

    private static BubbleShift shift(String assignedUser, String startTime, String endTime) {
        BubbleShift s = new BubbleShift();
        s.setAssignedUser(assignedUser);
        s.setStartTime(startTime);
        s.setEndTime(endTime);
        return s;
    }

    @Test
    void noAvailabilityRecordOnFile_isTreatedAsUnrestricted_noAvailabilityIssue() {
        BubbleUser w1 = worker("w1", "Jane");
        // Tuesday 2026-06-30, no availability record for w1 at all.
        BubbleShift s = shift("w1", "2026-06-30T08:00:00Z", "2026-06-30T12:00:00Z");

        ValidationReport report = validator.validate(List.of(s), List.of(w1), Map.of());

        assertThat(report.getIssues()).extracting(ValidationIssue::getRule)
                .doesNotContain("AVAILABILITY_DAY", "AVAILABILITY_HOURS");
    }

    @Test
    void shiftOnDayNotInAvailableDays_isRejected() {
        BubbleUser w1 = worker("w1", "Jane");
        BubbleAvailability availability = new BubbleAvailability();
        availability.setAvailableDays(List.of("Monday", "Wednesday", "Thursday", "Friday")); // no Tuesday

        // Tuesday 2026-06-30 - a day the worker is explicitly unavailable on.
        BubbleShift s = shift("w1", "2026-06-30T08:00:00Z", "2026-06-30T12:00:00Z");

        ValidationReport report = validator.validate(List.of(s), List.of(w1), Map.of("w1", availability));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getIssues()).extracting(ValidationIssue::getRule).contains("AVAILABILITY_DAY");
    }

    @Test
    void shiftOnAllowedDayButOutsideWorkdayHourWindow_isRejected() {
        BubbleUser w1 = worker("w1", "Jane");
        BubbleAvailability availability = new BubbleAvailability();
        availability.setAvailableDays(List.of("Monday"));
        availability.setWorkdayStartHour(9);
        availability.setWorkdayEndHour(17);

        // Monday 2026-06-29, but starts at 06:00 - before the 09:00 workday start.
        BubbleShift s = shift("w1", "2026-06-29T06:00:00Z", "2026-06-29T10:00:00Z");

        ValidationReport report = validator.validate(List.of(s), List.of(w1), Map.of("w1", availability));

        assertThat(report.isValid()).isFalse();
        assertThat(report.getIssues()).extracting(ValidationIssue::getRule).contains("AVAILABILITY_HOURS");
    }

    @Test
    void shiftOnAllowedDayAndWithinHourWindow_producesNoAvailabilityIssue() {
        BubbleUser w1 = worker("w1", "Jane");
        BubbleAvailability availability = new BubbleAvailability();
        availability.setAvailableDays(List.of("Monday"));
        availability.setWorkdayStartHour(6);
        availability.setWorkdayEndHour(18);

        // Monday 2026-06-29, 08:00-12:00 UTC = 11:00-15:00 Europe/Tallinn (summer, UTC+3) - within window.
        BubbleShift s = shift("w1", "2026-06-29T08:00:00Z", "2026-06-29T12:00:00Z");

        ValidationReport report = validator.validate(List.of(s), List.of(w1), Map.of("w1", availability));

        assertThat(report.getIssues()).extracting(ValidationIssue::getRule)
                .doesNotContain("AVAILABILITY_DAY", "AVAILABILITY_HOURS");
    }

    @Test
    void weekendShift_isCheckedAgainstWeekendWindow_notWorkdayWindow() {
        BubbleUser w1 = worker("w1", "Jane");
        BubbleAvailability availability = new BubbleAvailability();
        availability.setAvailableDays(List.of("Saturday"));
        // Deliberately narrow/absent workday window; weekend window is what should apply on a Saturday.
        availability.setWorkdayStartHour(9);
        availability.setWorkdayEndHour(10);
        availability.setWeekendStartHour(10);
        availability.setWeekendEndHour(16);

        // Saturday 2026-07-04, 11:00-14:00 Europe/Tallinn (UTC+3) - within the weekend window.
        BubbleShift s = shift("w1", "2026-07-04T08:00:00Z", "2026-07-04T11:00:00Z");

        ValidationReport report = validator.validate(List.of(s), List.of(w1), Map.of("w1", availability));

        assertThat(report.getIssues()).extracting(ValidationIssue::getRule)
                .doesNotContain("AVAILABILITY_DAY", "AVAILABILITY_HOURS");
    }

    @Test
    void twoArgOverload_skipsAvailabilityChecksEntirely_forBackwardCompatibility() {
        BubbleUser w1 = worker("w1", "Jane");
        // Tuesday shift with no availability map passed at all (old call signature).
        BubbleShift s = shift("w1", "2026-06-30T08:00:00Z", "2026-06-30T12:00:00Z");

        ValidationReport report = validator.validate(List.of(s), List.of(w1));

        assertThat(report.getIssues()).extracting(ValidationIssue::getRule)
                .doesNotContain("AVAILABILITY_DAY", "AVAILABILITY_HOURS");
    }
}
