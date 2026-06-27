package com.example.scheduler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonProperty("Assigned Company")
    private String assignedCompany;

    @JsonProperty("Type")
    private String type;

    public BubbleShift(String id, String assignedUser, String startTime, String endTime, String notes) {
        this.id = id;
        this.assignedUser = assignedUser;
        this.startTime = startTime;
        this.endTime = endTime;
        this.notes = notes;
    }
}
