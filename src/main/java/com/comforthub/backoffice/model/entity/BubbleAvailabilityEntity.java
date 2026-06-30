package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "availability")
@Data
@NoArgsConstructor
public class BubbleAvailabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "bubble_id", unique = true)
    private String bubbleId;

    @Column(name = "thing_type")
    private String thingType;

    @Column(name = "thing_id")
    private String thingId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "available_days", columnDefinition = "text[]")
    private String[] availableDays;

    @Column(name = "workday_start_hour")
    private Integer workdayStartHour;

    @Column(name = "workday_end_hour")
    private Integer workdayEndHour;

    @Column(name = "weekend_start_hour")
    private Integer weekendStartHour;

    @Column(name = "weekend_end_hour")
    private Integer weekendEndHour;
}
