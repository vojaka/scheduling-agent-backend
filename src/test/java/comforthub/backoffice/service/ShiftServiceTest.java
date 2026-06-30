package com.comforthub.backoffice.service;

import com.comforthub.backoffice.dto.ShiftWriteRequest;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ShiftServiceTest {

    private BubbleShiftRepository repository;
    private ShiftService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(BubbleShiftRepository.class);
        service = new ShiftService(repository);
        when(repository.save(any(BubbleShiftEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ShiftWriteRequest request(String start, String end) {
        ShiftWriteRequest r = new ShiftWriteRequest();
        r.setAssignedUser("u1");
        r.setStartTime(start);
        r.setEndTime(end);
        return r;
    }

    @Test
    void createParsesTimesAndAssignsId() {
        BubbleShiftEntity created = service.create(request("2026-06-29T08:00:00Z", "2026-06-29T16:00:00Z"));

        assertNotNull(created.getId());
        assertTrue(created.getId().startsWith("local-"));
        assertEquals(OffsetDateTime.parse("2026-06-29T08:00:00Z"), created.getStartTime());
        assertEquals("u1", created.getAssignedUser());
    }

    @Test
    void createRejectsEndBeforeStart() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(request("2026-06-29T16:00:00Z", "2026-06-29T08:00:00Z")));
        assertTrue(ex.getMessage().contains("endTime must be after startTime"));
    }

    @Test
    void createRejectsUnparseableTime() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(request("not-a-date", "2026-06-29T16:00:00Z")));
    }

    @Test
    void updateMissingReturnsEmpty() {
        when(repository.findById("nope")).thenReturn(Optional.empty());
        Optional<BubbleShiftEntity> result = service.update("nope", request("2026-06-29T08:00:00Z", "2026-06-29T16:00:00Z"));
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteMissingReturnsFalse() {
        when(repository.existsById("nope")).thenReturn(false);
        assertFalse(service.delete("nope"));
    }
}
