package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.model.BubbleShift;
import com.comforthub.backoffice.model.BubbleUser;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleAvailabilityRepository;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.repository.BubbleWageRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BubbleSyncServiceTest {

    private BubbleClient bubbleClient;
    private BubbleUserRepository userRepository;
    private BubbleStoreRepository storeRepository;
    private BubbleAvailabilityRepository availabilityRepository;
    private BubbleWageRateRepository wageRateRepository;
    private BubbleShiftRepository shiftRepository;
    private BubbleSyncService service;

    @BeforeEach
    void setUp() {
        bubbleClient = Mockito.mock(BubbleClient.class);
        userRepository = Mockito.mock(BubbleUserRepository.class);
        storeRepository = Mockito.mock(BubbleStoreRepository.class);
        availabilityRepository = Mockito.mock(BubbleAvailabilityRepository.class);
        wageRateRepository = Mockito.mock(BubbleWageRateRepository.class);
        shiftRepository = Mockito.mock(BubbleShiftRepository.class);

        // Empty defaults; individual tests override what they care about.
        when(bubbleClient.fetchUsers()).thenReturn(Collections.emptyList());
        when(bubbleClient.fetchStores()).thenReturn(Collections.emptyList());
        when(bubbleClient.fetchAvailability()).thenReturn(Collections.emptyList());
        when(bubbleClient.fetchWageRates()).thenReturn(Collections.emptyList());
        when(bubbleClient.fetchShifts()).thenReturn(Collections.emptyList());

        service = new BubbleSyncService(bubbleClient, userRepository, storeRepository,
                availabilityRepository, wageRateRepository, shiftRepository);
    }

    @Test
    void syncMapsUserFieldsAndPersistsViaRepository() {
        when(bubbleClient.fetchUsers()).thenReturn(List.of(
                new BubbleUser("u1", "Kim Smirnov", "Worker", 40, true)));

        String result = service.syncNow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BubbleUserEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository).saveAll(captor.capture());

        BubbleUserEntity saved = captor.getValue().get(0);
        assertEquals("u1", saved.getId());
        assertEquals("Kim Smirnov", saved.getName());
        assertEquals("Worker", saved.getRole());
        assertEquals(40, saved.getMaxHours());
        assertEquals(true, saved.getActive());
        assertTrue(result.contains("Status: Success"));
    }

    @Test
    void syncParsesShiftTimestampsToOffsetDateTime() {
        BubbleShift shift = new BubbleShift("s1", "u1",
                "2026-06-29T08:00:00Z", "2026-06-29T16:00:00Z", "Morning");
        when(bubbleClient.fetchShifts()).thenReturn(List.of(shift));

        service.syncNow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BubbleShiftEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepository).saveAll(captor.capture());

        BubbleShiftEntity saved = captor.getValue().get(0);
        assertEquals("s1", saved.getId());
        assertEquals(OffsetDateTime.parse("2026-06-29T08:00:00Z"), saved.getStartTime());
        assertEquals(OffsetDateTime.parse("2026-06-29T16:00:00Z"), saved.getEndTime());
    }

    @Test
    void syncToleratesUnparseableTimestamp() {
        BubbleShift shift = new BubbleShift("s1", "u1", "not-a-date", null, "Bad");
        when(bubbleClient.fetchShifts()).thenReturn(List.of(shift));

        String result = service.syncNow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BubbleShiftEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepository).saveAll(captor.capture());
        assertEquals(null, captor.getValue().get(0).getStartTime());
        assertTrue(result.contains("Status: Success"));
    }

    @Test
    void syncRunsAllFiveRepositories() {
        service.syncNow();
        verify(userRepository).saveAll(anyList());
        verify(storeRepository).saveAll(anyList());
        verify(availabilityRepository).saveAll(anyList());
        verify(wageRateRepository).saveAll(anyList());
        verify(shiftRepository).saveAll(anyList());
    }
}
