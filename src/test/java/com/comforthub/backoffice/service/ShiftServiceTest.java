package com.comforthub.backoffice.service;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.dto.ShiftWriteRequest;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private BubbleShiftRepository shiftRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private BubbleClient bubbleClient;

    @InjectMocks
    private ShiftService shiftService;

    private ShiftWriteRequest createRequest(String startTime, String endTime, String assignedUser, String assignedStore, String type, String status, String notes) {
        ShiftWriteRequest request = new ShiftWriteRequest();
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setAssignedUser(assignedUser);
        request.setAssignedStore(assignedStore);
        request.setType(type);
        request.setStatus(status);
        request.setNotes(notes);
        return request;
    }

    @Test
    void create_savesNewShiftWithCompanyContext() {
        ShiftWriteRequest request = createRequest("2026-07-01T08:00:00Z", "2026-07-01T16:00:00Z", "user-1", "store-1", "STANDARD", "COMMITTED", "notes");
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        
        BubbleShiftEntity savedEntity = new BubbleShiftEntity();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setAssignedCompany("company-1");
        savedEntity.setAssignedUser("user-1");
        
        when(shiftRepository.save(any(BubbleShiftEntity.class))).thenReturn(savedEntity);

        BubbleShiftEntity result = shiftService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getAssignedCompany()).isEqualTo("company-1");

        ArgumentCaptor<BubbleShiftEntity> captor = ArgumentCaptor.forClass(BubbleShiftEntity.class);
        verify(shiftRepository).save(captor.capture());
        BubbleShiftEntity captured = captor.getValue();
        assertThat(captured.getAssignedUser()).isEqualTo("user-1");
        assertThat(captured.getAssignedCompany()).isEqualTo("company-1");
        assertThat(captured.getStartTime()).isEqualTo(OffsetDateTime.parse("2026-07-01T08:00:00Z"));
        assertThat(captured.getEndTime()).isEqualTo(OffsetDateTime.parse("2026-07-01T16:00:00Z"));
        assertThat(captured.getType()).isEqualTo("STANDARD");
        assertThat(captured.getStatus()).isEqualTo("COMMITTED");
        assertThat(captured.getNotes()).isEqualTo("notes");
    }

    // ---------------------------------------------- #114 Bubble write-through

    @Test
    @SuppressWarnings("unchecked")
    void create_writesThroughToBubble_withVerifiedRawKeys_andKeepsBubbleId() {
        ShiftWriteRequest request = createRequest("2026-07-01T08:00:00Z", "2026-07-01T16:00:00Z", "user-1", "store-1", "STANDARD", "COMMITTED", "notes");
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.create(eq("shift"), any())).thenReturn("bubble-shift-1");
        when(shiftRepository.save(any(BubbleShiftEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BubbleShiftEntity result = shiftService.create(request);

        // The mirror row keeps the Bubble id, so the hourly ETL upsert is dedupe-safe.
        assertThat(result.getBubbleId()).isEqualTo("bubble-shift-1");

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(bubbleClient).create(eq("shift"), bodyCaptor.capture());
        Map<String, Object> body = (Map<String, Object>) bodyCaptor.getValue();
        // Exact raw keys sync.py#sync_shifts reads back.
        assertThat(body.get("assigned_user_user")).isEqualTo("user-1");
        assertThat(body.get("start_time_date")).isEqualTo("2026-07-01T08:00:00Z");
        assertThat(body.get("end_time_date")).isEqualTo("2026-07-01T16:00:00Z");
        assertThat(body.get("notes_text")).isEqualTo("notes");
        assertThat(body.get("assigned_company_custom____merchant")).isEqualTo("company-1");
        assertThat(body.get("type_option_shift_type")).isEqualTo("STANDARD");
        assertThat(body.get("status_option_shift_approval_status")).isEqualTo("COMMITTED");
        assertThat(body.get("assigned_store_custom_store")).isEqualTo("store-1");
    }

    @Test
    void create_bubbleFailure_isBestEffort_mirrorStillSaved() {
        ShiftWriteRequest request = createRequest("2026-07-01T08:00:00Z", "2026-07-01T16:00:00Z", "user-1", "store-1", "STANDARD", "COMMITTED", "notes");
        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(bubbleClient.create(eq("shift"), any())).thenThrow(new RuntimeException("bubble down"));
        when(shiftRepository.save(any(BubbleShiftEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BubbleShiftEntity result = shiftService.create(request);

        assertThat(result.getBubbleId()).isNull();
        verify(shiftRepository).save(any(BubbleShiftEntity.class));
    }

    @Test
    void update_patchesBubbleRecord_whenMirrorRowHasBubbleId() {
        UUID shiftId = UUID.randomUUID();
        BubbleShiftEntity existing = new BubbleShiftEntity();
        existing.setId(shiftId);
        existing.setBubbleId("bubble-shift-1");
        existing.setAssignedCompany("company-1");

        ShiftWriteRequest request = createRequest("2026-07-01T09:00:00Z", "2026-07-01T17:00:00Z", "user-1", "store-1", "STANDARD", "PROPOSED", "n");

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(existing));
        when(shiftRepository.save(any(BubbleShiftEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(shiftService.update(shiftId, request)).isPresent();

        verify(bubbleClient).update(eq("shift"), eq("bubble-shift-1"), any());
        verify(bubbleClient, never()).create(any(), any());
    }

    @Test
    void update_createsBubbleRecord_whenMirrorRowNeverReachedBubble() {
        UUID shiftId = UUID.randomUUID();
        BubbleShiftEntity existing = new BubbleShiftEntity();
        existing.setId(shiftId);
        existing.setAssignedCompany("company-1");

        ShiftWriteRequest request = createRequest("2026-07-01T09:00:00Z", "2026-07-01T17:00:00Z", "user-1", "store-1", "STANDARD", "PROPOSED", "n");

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(existing));
        when(bubbleClient.create(eq("shift"), any())).thenReturn("bubble-shift-new");
        when(shiftRepository.save(any(BubbleShiftEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<BubbleShiftEntity> result = shiftService.update(shiftId, request);

        assertThat(result).isPresent();
        assertThat(result.get().getBubbleId()).isEqualTo("bubble-shift-new");
        verify(bubbleClient, never()).update(any(), any(), any());
    }

    @Test
    void delete_alsoDeletesBubbleRecord_whenBubbleIdPresent() {
        UUID shiftId = UUID.randomUUID();
        BubbleShiftEntity existing = new BubbleShiftEntity();
        existing.setId(shiftId);
        existing.setBubbleId("bubble-shift-1");
        existing.setAssignedCompany("company-1");

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(existing));

        assertThat(shiftService.delete(shiftId)).isTrue();

        verify(bubbleClient).delete("shift", "bubble-shift-1");
        verify(shiftRepository).delete(existing);
    }

    @Test
    void delete_skipsBubble_whenRowNeverReachedBubble() {
        UUID shiftId = UUID.randomUUID();
        BubbleShiftEntity existing = new BubbleShiftEntity();
        existing.setId(shiftId);
        existing.setAssignedCompany("company-1");

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(existing));

        assertThat(shiftService.delete(shiftId)).isTrue();

        verify(bubbleClient, never()).delete(any(), any());
        verify(shiftRepository).delete(existing);
    }

    // ------------------------------------------------------- existing suite

    @Test
    void create_failsWithInvalidTime() {
        ShiftWriteRequest request = createRequest("invalid-time", "2026-07-01T16:00:00Z", "user-1", "store-1", "STANDARD", "COMMITTED", "notes");
        assertThatThrownBy(() -> shiftService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime and endTime must be valid ISO-8601 timestamps");
    }

    @Test
    void create_failsWithEndBeforeStart() {
        ShiftWriteRequest request = createRequest("2026-07-01T16:00:00Z", "2026-07-01T08:00:00Z", "user-1", "store-1", "STANDARD", "COMMITTED", "notes");
        assertThatThrownBy(() -> shiftService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime must be after startTime");
    }

    @Test
    void update_modifiesExistingShiftWithinCompany() {
        UUID shiftId = UUID.randomUUID();
        BubbleShiftEntity existing = new BubbleShiftEntity();
        existing.setId(shiftId);
        existing.setAssignedCompany("company-1");
        existing.setAssignedUser("user-1");

        ShiftWriteRequest request = createRequest("2026-07-01T09:00:00Z", "2026-07-01T17:00:00Z", "user-updated", "store-2", "STANDARD", "PROPOSED", "updated notes");

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(existing));
        when(shiftRepository.save(any(BubbleShiftEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<BubbleShiftEntity> result = shiftService.update(shiftId, request);

        assertThat(result).isPresent();
        BubbleShiftEntity updated = result.get();
        assertThat(updated.getAssignedUser()).isEqualTo("user-updated");
        assertThat(updated.getAssignedCompany()).isEqualTo("company-1");
        assertThat(updated.getStartTime()).isEqualTo(OffsetDateTime.parse("2026-07-01T09:00:00Z"));
        assertThat(updated.getEndTime()).isEqualTo(OffsetDateTime.parse("2026-07-01T17:00:00Z"));
        assertThat(updated.getNotes()).isEqualTo("updated notes");
        assertThat(updated.getStatus()).isEqualTo("PROPOSED");
    }

    @Test
    void update_failsForForeignCompany() {
        UUID shiftId = UUID.randomUUID();
        BubbleShiftEntity existing = new BubbleShiftEntity();
        existing.setId(shiftId);
        existing.setAssignedCompany("company-other");
        existing.setAssignedUser("user-1");

        ShiftWriteRequest request = createRequest("2026-07-01T09:00:00Z", "2026-07-01T17:00:00Z", "user-updated", "store-2", "STANDARD", "PROPOSED", "updated notes");

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(existing));

        Optional<BubbleShiftEntity> result = shiftService.update(shiftId, request);

        assertThat(result).isEmpty();
        verify(shiftRepository, never()).save(any());
        verify(bubbleClient, never()).update(any(), any(), any());
        verify(bubbleClient, never()).create(any(), any());
    }

    @Test
    void delete_removesShiftWithinCompany() {
        UUID shiftId = UUID.randomUUID();
        BubbleShiftEntity existing = new BubbleShiftEntity();
        existing.setId(shiftId);
        existing.setAssignedCompany("company-1");

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(existing));

        boolean deleted = shiftService.delete(shiftId);

        assertThat(deleted).isTrue();
        verify(shiftRepository).delete(existing);
    }

    @Test
    void delete_failsForForeignCompany() {
        UUID shiftId = UUID.randomUUID();
        BubbleShiftEntity existing = new BubbleShiftEntity();
        existing.setId(shiftId);
        existing.setAssignedCompany("company-other");

        when(currentUserService.currentCompanyId()).thenReturn(Optional.of("company-1"));
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(existing));

        boolean deleted = shiftService.delete(shiftId);

        assertThat(deleted).isFalse();
        verify(shiftRepository, never()).delete(any());
        verify(bubbleClient, never()).delete(any(), any());
    }
}
