package com.comforthub.backoffice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BubbleAvailability {

    @JsonProperty("_id")
    private String id;

    /** "Store", "Worker", "User" */
    @JsonProperty("thing_option_things")
    private String thingType;

    /** The Bubble ID of the linked store / worker */
    @JsonProperty("thing_id_text")
    private String thingId;

    /** Opening days e.g. ["Monday","Tuesday",...] */
    @JsonProperty("usual_available_days_list_option_calendar_days")
    private List<String> availableDays;

    /** Workday open hour (0-23) */
    @JsonProperty("workday_availability___start_number")
    private Integer workdayStartHour;

    /** Workday close hour (0-23) */
    @JsonProperty("workday_availability___end_number")
    private Integer workdayEndHour;

    /** Weekend open hour (0-23) */
    @JsonProperty("weekend_availability___start_number")
    private Integer weekendStartHour;

    /** Weekend close hour (0-23) */
    @JsonProperty("weekend_availability___end_number")
    private Integer weekendEndHour;
}
