package com.example.scheduler.model;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"Assigned User", "assigned_user_user"})
    private String assignedUser;

    @JsonProperty("Time - Start Time")
    @JsonAlias({"Time - Start Time", "start_time_date"})
    private String startTime;

    @JsonProperty("Time - End Time")
    @JsonAlias({"Time - End Time", "end_time_date"})
    private String endTime;

    @JsonProperty("notes")
    @JsonAlias({"notes", "notes_text"})
    private String notes;

    @JsonProperty("Assigned Company")
    @JsonAlias({"Assigned Company", "assigned_company_custom____merchant"})
    private String assignedCompany;

    @JsonProperty("Type")
    @JsonAlias({"Type", "type_option_shift_type"})
    private String type;

    @JsonProperty("Status")
    @JsonAlias({"Status", "status_option_shift_approval_status"})
    private String status;

    @JsonProperty("Assigned Store")
    @JsonAlias({"Assigned Store", "assigned_store_custom_store"})
    private String assignedStore;


    public BubbleShift(String id, String assignedUser, String startTime, String endTime, String notes) {
        this.id = id;
        this.assignedUser = assignedUser;
        this.startTime = startTime;
        this.endTime = endTime;
        this.notes = notes;
    }
}
