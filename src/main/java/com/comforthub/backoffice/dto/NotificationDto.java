package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object mirroring the Bubble.io 'notifications' schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDto {
    private String id; // maps to unique ID

    @JsonProperty("duration")
    private Double duration;

    @JsonProperty("errorCode")
    private Integer errorCode;

    @JsonProperty("externalMessage")
    private String externalMessage;

    @JsonProperty("internalMessage")
    private String internalMessage;

    @JsonProperty("subThings")
    private String subThings;

    @JsonProperty("thing")
    private String thing; // polymorphic thing reference ID

    @JsonProperty("thingType")
    private String thingType; // Type of the linked thing (e.g. order, user, inventory)

    @JsonProperty("title")
    private String title;

    @JsonProperty("triggerVerifications")
    private Boolean triggerVerifications;

    @JsonProperty("visibleFor")
    private List<String> visibleFor;

    @JsonProperty("workflowCode")
    private String workflowCode;

    @JsonProperty("isViewed")
    private Boolean isViewed; // maps to isViewed / IsViewed in Bubble

    @JsonProperty("company")
    private String company; // maps to Company in Bubble

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("modifiedAt")
    private String modifiedAt;
}
