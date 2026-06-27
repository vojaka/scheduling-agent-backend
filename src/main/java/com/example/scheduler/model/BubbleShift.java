package com.example.scheduler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BubbleShift {
    @JsonProperty("_id")
    private String id;

    @JsonProperty("Assigned User")
    private String assignedUser;

    @JsonProperty("Time - Start Time")
    private String startTime;

    @JsonProperty("Time - End Time")
    private String endTime;

    @JsonProperty("notes")
    private String notes;
}
