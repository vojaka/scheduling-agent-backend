package com.comforthub.backoffice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bubble_availability")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BubbleAvailabilityEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

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
