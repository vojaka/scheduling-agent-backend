package com.example.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerateRequest {
    private String prompt;
    private Boolean commit;
    private String month;
    private String company;
    private java.util.List<String> workers;

    /**
     * Optional minutes to schedule workers BEFORE store opening.
     * E.g. 30 means a worker can start 30 min before the store opens for setup/prep.
     */
    private Integer bufferBeforeMinutes;

    /**
     * Optional minutes to schedule workers AFTER store closing.
     * E.g. 30 means a worker can stay 30 min after closing for cleanup.
     */
    private Integer bufferAfterMinutes;
}
