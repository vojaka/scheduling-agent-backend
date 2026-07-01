package com.comforthub.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Availability payload exchanged with the React backoffice UI — the opening
 * hours/days profile attached to a store or a worker.
 *
 * <p>Field names mirror the former JPA {@code BubbleAvailabilityEntity} JSON so
 * the UI needs no changes; ids are Bubble text ids instead of UUIDs (invisible
 * over the wire). Hours are 0–23. {@code availableDays} holds weekday names
 * (e.g. {@code ["Monday", ...]}) matching Bubble's {@code calendar days} option set.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvailabilityDto {

    /** Bubble record id (the Data API {@code _id}). */
    private String id;

    /** Same Bubble id, kept for parity with the old {@code bubbleId} field. */
    private String bubbleId;

    /**
     * What the profile is attached to — a value of Bubble's {@code things}
     * option set (e.g. {@code "Store"} / {@code "Worker"} / {@code "User"}).
     */
    private String thingType;

    /** Bubble id of the linked store / worker. */
    private String thingId;

    /** Opening days, e.g. {@code ["Monday", "Tuesday", ...]}. */
    private List<String> availableDays;

    /** Workday open hour (0–23). */
    private Integer workdayStartHour;

    /** Workday close hour (0–23). */
    private Integer workdayEndHour;

    /** Weekend open hour (0–23). */
    private Integer weekendStartHour;

    /** Weekend close hour (0–23). */
    private Integer weekendEndHour;

    /** ISO-8601 instant (Bubble "Created Date"). */
    private String createdAt;
}
