package com.comforthub.backoffice.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BubbleWageRate {
    @JsonProperty("_id")
    private String id;

    @JsonProperty("Company")
    @JsonAlias({"company", "company_custom____merchant", "company_custom____company"})
    private String company;

    @JsonProperty("Rate")
    @JsonAlias({"rate", "rate_number"})
    private BigDecimal rate;

    @JsonProperty("User")
    @JsonAlias({"user", "user_user"})
    private String user;
}
