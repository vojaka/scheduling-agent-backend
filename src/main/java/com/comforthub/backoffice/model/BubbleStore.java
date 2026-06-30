package com.comforthub.backoffice.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BubbleStore {
    @JsonProperty("_id")
    private String id;

    @JsonProperty("Store Name")
    @JsonAlias({"Store Name", "store_name_text"})
    private String name;

    @JsonProperty("Company")
    @JsonAlias({"Company", "company__single__custom____merchant"})
    private String company;

    @JsonProperty("Availability")
    @JsonAlias({"Availability", "availability_custom_worker_availability"})
    private String availabilityId;

    @JsonProperty("isDeleted")
    @JsonAlias({"isDeleted", "isdeleted_boolean"})
    private Boolean isDeleted;
}
