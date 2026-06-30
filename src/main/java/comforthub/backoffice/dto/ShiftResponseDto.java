package com.comforthub.backoffice.dto;

import com.comforthub.backoffice.model.BubbleShift;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
public class ShiftResponseDto {
    private String id;

    @JsonProperty("Assigned User")
    private String assignedUser;

    @JsonProperty("Time - Start Time")
    private String startTime;

    @JsonProperty("Time - End Time")
    private String endTime;

    private String notes;
    private String shiftType;
    private boolean isCancellable;
    private boolean isModifiable;

    @JsonProperty("Assigned Company")
    private String assignedCompany;

    @JsonProperty("Type")
    private String type;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("Assigned Store")
    private String assignedStore;

    public ShiftResponseDto(BubbleShift shift) {
        this.id = shift.getId();
        this.assignedUser = shift.getAssignedUser();
        this.startTime = shift.getStartTime();
        this.endTime = shift.getEndTime();
        this.notes = shift.getNotes();
        this.assignedCompany = shift.getAssignedCompany();
        this.type = shift.getType();
        this.status = shift.getStatus();
        this.assignedStore = shift.getAssignedStore();

        calculateDynamicFields();
    }

    private void calculateDynamicFields() {
        if (this.startTime == null) {
            this.shiftType = "STANDARD";
            this.isCancellable = true;
            this.isModifiable = true;
            return;
        }

        try {
            Instant startInstant = Instant.parse(this.startTime);
            Instant endInstant = this.endTime != null ? Instant.parse(this.endTime) : startInstant;

            ZoneId estoniaZone = ZoneId.of("Europe/Tallinn");
            ZonedDateTime startEst = ZonedDateTime.ofInstant(startInstant, estoniaZone);
            ZonedDateTime endEst = ZonedDateTime.ofInstant(endInstant, estoniaZone);
            ZonedDateTime nowEst = ZonedDateTime.now(estoniaZone);

            this.isCancellable = startEst.isAfter(nowEst);
            this.isModifiable = startEst.isAfter(nowEst);

            // Night shift: any part between 22:00 and 06:00 (Estonian time)
            boolean isNight = false;
            ZonedDateTime current = startEst;
            while (current.isBefore(endEst)) {
                int hour = current.getHour();
                if (hour >= 22 || hour < 6) {
                    isNight = true;
                    break;
                }
                current = current.plusMinutes(15);
            }
            this.shiftType = isNight ? "NIGHT" : "STANDARD";

        } catch (Exception e) {
            this.shiftType = "STANDARD";
            this.isCancellable = true;
            this.isModifiable = true;
        }
    }
}
