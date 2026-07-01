package com.comforthub.backoffice.service;

import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleStore;
import com.comforthub.backoffice.model.BubbleUser;
import com.comforthub.backoffice.model.BubbleWageRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GeminiPromptBuilder}: backend #4 (compact/trimmed prompt payload) and
 * backend #2 (per-worker availability constraint injection).
 */
class GeminiPromptBuilderTest {

    private final GeminiPromptBuilder builder = new GeminiPromptBuilder();

    // ----------------------------------------------------------- backend #2: availability text

    @Test
    void formatAvailability_returnsUnrestricted_whenNoRecordOnFile() {
        assertThat(builder.formatAvailability(null)).isEqualTo("unrestricted");
    }

    @Test
    void formatAvailability_returnsUnrestricted_whenRecordHasNoUsableFields() {
        BubbleAvailability availability = new BubbleAvailability();
        assertThat(builder.formatAvailability(availability)).isEqualTo("unrestricted");
    }

    @Test
    void formatAvailability_workerUnavailableOnTuesday_isReflectedAsAnExplicitDayOff() {
        // Worker available Mon/Wed/Thu/Fri only - Tuesday must show up as excluded.
        BubbleAvailability availability = new BubbleAvailability();
        availability.setAvailableDays(List.of("Monday", "Wednesday", "Thursday", "Friday"));

        String text = builder.formatAvailability(availability);

        assertThat(text).contains("days=Mon,Wed,Thu,Fri");
        assertThat(text).contains("off=Tue,Sat,Sun");
        // The allowed-days list itself must not mention Tuesday.
        String daysSegment = text.split(";")[0];
        assertThat(daysSegment).doesNotContain("Tue");
    }

    @Test
    void formatAvailability_includesWorkdayAndWeekendHourWindows() {
        BubbleAvailability availability = new BubbleAvailability();
        availability.setWorkdayStartHour(8);
        availability.setWorkdayEndHour(17);
        availability.setWeekendStartHour(10);
        availability.setWeekendEndHour(15);

        String text = builder.formatAvailability(availability);

        assertThat(text).contains("wd=08-17");
        assertThat(text).contains("we=10-15");
    }

    // ----------------------------------------------------------- backend #4: compact worker context

    @Test
    void buildWorkerContext_usesCompactPipeDelimitedFields_notVerboseLabels() {
        BubbleUser worker = new BubbleUser();
        worker.setId("w1");
        worker.setName("Jane Doe");
        worker.setRole("Worker");
        worker.setMaxHours(40);

        String context = builder.buildWorkerContext(List.of(worker), Map.of(), Map.of());

        assertThat(context).contains("w1|Jane Doe|Worker|40|-|unrestricted");
        // The old verbose per-worker sentence format must be gone.
        assertThat(context).doesNotContain("ID:").doesNotContain("Max Weekly Hours:");
    }

    @Test
    void buildWorkerContext_injectsPerWorkerAvailabilityConstraint() {
        BubbleUser worker = new BubbleUser();
        worker.setId("w1");
        worker.setName("Jane Doe");
        worker.setRole("Worker");

        BubbleAvailability availability = new BubbleAvailability();
        availability.setAvailableDays(List.of("Monday", "Wednesday", "Friday"));
        availability.setWorkdayStartHour(9);
        availability.setWorkdayEndHour(18);

        String context = builder.buildWorkerContext(List.of(worker), Map.of(), Map.of("w1", availability));

        assertThat(context).contains("days=Mon,Wed,Fri");
        assertThat(context).contains("off=Tue,Thu,Sat,Sun");
        assertThat(context).contains("wd=09-18");
    }

    @Test
    void buildWorkerContext_mergesWageRateIntoTheSameLine_insteadOfARedundantSection() {
        BubbleUser worker = new BubbleUser();
        worker.setId("w1");
        worker.setName("Jane Doe");

        Map<String, BigDecimal> rateById = Map.of("w1", new BigDecimal("12.50"));

        String context = builder.buildWorkerContext(List.of(worker), rateById, Map.of());

        assertThat(context).contains("12.5");
        assertThat(context).doesNotContain("Wage Rates");
    }

    @Test
    void buildWorkerContext_returnsPlaceholder_whenNoWorkers() {
        assertThat(builder.buildWorkerContext(List.of(), Map.of(), Map.of())).isEqualTo("Workers: none.");
    }

    // ----------------------------------------------------------- backend #4: rate lookup resolution

    @Test
    void buildRateLookup_resolvesByBothWorkerIdAndWorkerName() {
        BubbleUser worker = new BubbleUser();
        worker.setId("w1");
        worker.setName("Jane Doe");

        BubbleWageRate rate = new BubbleWageRate();
        rate.setUser("Jane Doe"); // Bubble wage rows reference the worker by name in some records
        rate.setRate(new BigDecimal("15.00"));

        Map<String, BigDecimal> lookup = builder.buildRateLookup(List.of(rate), List.of(worker));

        assertThat(lookup.get("w1")).isEqualByComparingTo("15.00");
        assertThat(lookup.get("Jane Doe")).isEqualByComparingTo("15.00");
    }

    // ----------------------------------------------------------- backend #4: store context trimming

    @Test
    void buildStoreContext_returnsEmptyString_whenNoStoresGiven() {
        // Callers are expected to have already stripped inactive/deleted stores before calling this.
        assertThat(builder.buildStoreContext(List.of(), Map.of())).isEmpty();
    }

    @Test
    void buildStoreContext_formatsStoreHoursFromItsAvailabilityRecord() {
        BubbleStore store = new BubbleStore();
        store.setId("s1");
        store.setName("Downtown");
        store.setCompany("c1");

        BubbleAvailability availability = new BubbleAvailability();
        availability.setWorkdayStartHour(8);
        availability.setWorkdayEndHour(20);

        String context = builder.buildStoreContext(List.of(store), Map.of("s1", availability));

        assertThat(context).contains("Downtown|c1|wd=08-20");
    }

    // ----------------------------------------------------------- assembled prompt

    @Test
    void assemblePrompt_omitsStoreBlock_whenStoreContextIsBlank() {
        String prompt = builder.assemblePrompt("Monday, 2026-06-29", "2026-06-29",
                "Workers - ...\n- w1|Jane|Worker|-|-|unrestricted\n", "", "Prefer morning shifts.");

        assertThat(prompt).contains("Prefer morning shifts.");
        assertThat(prompt).doesNotContain("Stores -");
    }

    @Test
    void assemblePrompt_includesStoreBlock_whenPresent() {
        String prompt = builder.assemblePrompt("Monday, 2026-06-29", "2026-06-29",
                "Workers - ...\n", "Stores - ...\n- Downtown|c1|wd=08-20\n", "");

        assertThat(prompt).contains("Stores - ...");
        assertThat(prompt).contains("Downtown|c1|wd=08-20");
    }
}
