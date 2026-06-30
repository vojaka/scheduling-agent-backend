package com.comforthub.backoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerateRequest {
    private String prompt;
    private Boolean commit;
    private String month;
    private String company;
    private List<String> workers;

    /** Optional minutes to schedule workers BEFORE store opening (setup/prep). */
    private Integer bufferBeforeMinutes;

    /** Optional minutes to schedule workers AFTER store closing (cleanup). */
    private Integer bufferAfterMinutes;
}
