package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.dto.ScheduleGenerateResponse;
import com.comforthub.backoffice.model.BubbleAvailability;
import com.comforthub.backoffice.model.BubbleStore;
import com.comforthub.backoffice.model.BubbleUser;
import com.comforthub.backoffice.model.BubbleWageRate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScheduleOrchestrationService}'s data-prep pipeline: backend #4 (dropping
 * inactive workers/stores and unrelated wage records before the prompt is built) and backend #2
 * (indexing availability records by worker/store id).
 *
 * <p>These tests instantiate the service directly (not via the Spring context), so the
 * {@code @Value}-injected {@code geminiApiKey} field is never populated and generation always runs
 * in simulation mode — deliberately, so the filtering logic (which runs before the live/simulation
 * branch) can be exercised without a real Gemini HTTP call. The compact prompt payload itself is
 * covered directly in {@code GeminiPromptBuilderTest}.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleOrchestrationServiceTest {

    @Mock
    private BubbleClient bubbleClient;

    private ScheduleOrchestrationService service() {
        return new ScheduleOrchestrationService(
                bubbleClient, new DeterministicValidator(), new ObjectMapper(), new GeminiPromptBuilder());
    }

    private static BubbleUser worker(String id, String name, Boolean active) {
        BubbleUser w = new BubbleUser();
        w.setId(id);
        w.setName(name);
        w.setActive(active);
        return w;
    }

    private static BubbleWageRate wage(String user, String company, String rate) {
        BubbleWageRate r = new BubbleWageRate();
        r.setUser(user);
        r.setCompany(company);
        r.setRate(new BigDecimal(rate));
        return r;
    }

    private static BubbleStore store(String id, String name, String company, Boolean isDeleted) {
        BubbleStore s = new BubbleStore();
        s.setId(id);
        s.setName(name);
        s.setCompany(company);
        s.setIsDeleted(isDeleted);
        return s;
    }

    @Test
    void inactiveWorkersAreDroppedBeforeGeneration_andNeverAssignedAShift() {
        // Deliberately inactive-first so a naive "just take index 0" bug would surface immediately.
        BubbleUser inactive = worker("w1", "Inactive Ivo", false);
        BubbleUser active = worker("w2", "Active Anna", true);
        when(bubbleClient.fetchUsers()).thenReturn(List.of(inactive, active));
        when(bubbleClient.fetchWageRates()).thenReturn(List.of());
        when(bubbleClient.fetchStores()).thenReturn(List.of());
        when(bubbleClient.fetchAvailability()).thenReturn(List.of());

        ScheduleGenerateResponse response = service().generateSchedule(
                "Compliant week please", null, null, null, null, null);

        assertThat(response.getOrchestratorLogs()).anyMatch(l -> l.contains("Filtered to 1 active workers."));
        assertThat(response.getProposedShifts()).isNotEmpty();
        assertThat(response.getProposedShifts()).extracting(s -> s.getAssignedUser())
                .allMatch(u -> u.equals("Active Anna"));
    }

    @Test
    void nullActiveFlag_isTreatedAsActive_failOpenFallback() {
        BubbleUser legacyWorker = worker("w1", "Legacy Lars", null); // never had the field set
        when(bubbleClient.fetchUsers()).thenReturn(List.of(legacyWorker));
        when(bubbleClient.fetchWageRates()).thenReturn(List.of());
        when(bubbleClient.fetchStores()).thenReturn(List.of());
        when(bubbleClient.fetchAvailability()).thenReturn(List.of());

        ScheduleGenerateResponse response = service().generateSchedule(
                "Compliant week please", null, null, null, null, null);

        assertThat(response.getOrchestratorLogs()).anyMatch(l -> l.contains("Filtered to 1 active workers."));
    }

    @Test
    void wageRatesForFilteredOutWorkers_areExcludedFromTheRelevantCount() {
        BubbleUser keptWorker = worker("w1", "Kept Kaja", true);
        when(bubbleClient.fetchUsers()).thenReturn(List.of(keptWorker));
        when(bubbleClient.fetchWageRates()).thenReturn(List.of(
                wage("w1", "c1", "12.50"),
                wage("some-other-worker-not-in-the-pool", "c1", "9.00")
        ));
        when(bubbleClient.fetchStores()).thenReturn(List.of());
        when(bubbleClient.fetchAvailability()).thenReturn(List.of());

        ScheduleGenerateResponse response = service().generateSchedule(
                "Compliant week please", null, null, null, null, null);

        assertThat(response.getOrchestratorLogs())
                .anyMatch(l -> l.contains("Filtered to 1 relevant wage rates"));
    }

    @Test
    void deletedStoresAreExcludedFromTheActiveStoreCount() {
        when(bubbleClient.fetchUsers()).thenReturn(List.of(worker("w1", "Kaja", true)));
        when(bubbleClient.fetchWageRates()).thenReturn(List.of());
        when(bubbleClient.fetchStores()).thenReturn(List.of(
                store("s1", "Live Store", "c1", false),
                store("s2", "Deleted Store", "c1", true)
        ));
        when(bubbleClient.fetchAvailability()).thenReturn(List.of());

        ScheduleGenerateResponse response = service().generateSchedule(
                "Compliant week please", null, null, null, null, null);

        assertThat(response.getOrchestratorLogs()).anyMatch(l -> l.contains("1 active stores."));
    }

    @Test
    void availabilityRecords_areIndexedOnlyForWorkerAndUserThingTypes_notStores() {
        BubbleUser w1 = worker("w1", "Kaja", true);
        when(bubbleClient.fetchUsers()).thenReturn(List.of(w1));
        when(bubbleClient.fetchWageRates()).thenReturn(List.of());
        when(bubbleClient.fetchStores()).thenReturn(List.of());

        BubbleAvailability workerAvailability = new BubbleAvailability();
        workerAvailability.setThingType("Worker");
        workerAvailability.setThingId("w1");

        BubbleAvailability storeAvailability = new BubbleAvailability();
        storeAvailability.setThingType("Store");
        storeAvailability.setThingId("s1");

        when(bubbleClient.fetchAvailability()).thenReturn(List.of(workerAvailability, storeAvailability));

        ScheduleGenerateResponse response = service().generateSchedule(
                "Compliant week please", null, null, null, null, null);

        assertThat(response.getOrchestratorLogs())
                .anyMatch(l -> l.contains("Resolved availability for 1 of 1 workers"));
    }

    @Test
    void isWorkerOrUserThing_isCaseInsensitiveAndAcceptsBothOptionSetValues() {
        assertThat(ScheduleOrchestrationService.isWorkerOrUserThing("Worker")).isTrue();
        assertThat(ScheduleOrchestrationService.isWorkerOrUserThing("user")).isTrue();
        assertThat(ScheduleOrchestrationService.isWorkerOrUserThing("Store")).isFalse();
        assertThat(ScheduleOrchestrationService.isWorkerOrUserThing(null)).isFalse();
    }

    @Test
    void isStoreThing_isCaseInsensitive() {
        assertThat(ScheduleOrchestrationService.isStoreThing("Store")).isTrue();
        assertThat(ScheduleOrchestrationService.isStoreThing("store")).isTrue();
        assertThat(ScheduleOrchestrationService.isStoreThing("Worker")).isFalse();
        assertThat(ScheduleOrchestrationService.isStoreThing(null)).isFalse();
    }
}
