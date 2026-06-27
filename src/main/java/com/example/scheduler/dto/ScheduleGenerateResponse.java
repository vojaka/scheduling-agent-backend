package com.example.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerateResponse {
    private List<ShiftResponseDto> proposedShifts = new ArrayList<>();
    private ValidationReport validationReport;
    private List<String> orchestratorLogs = new ArrayList<>();
}
