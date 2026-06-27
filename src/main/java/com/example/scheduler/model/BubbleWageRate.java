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
public class BubbleWageRate {
    @JsonProperty("_id")
    private String id;

    @JsonProperty("Company")
    private String company;

    @JsonProperty("Rate")
    private Double rate;

    @JsonProperty("User")
    private String user;
}
