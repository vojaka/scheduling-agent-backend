package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shifts")
@Data
@NoArgsConstructor
public class BubbleShiftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "bubble_id", unique = true)
    private String bubbleId;

    @Column(name = "assigned_user")
    private String assignedUser;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "notes")
    private String notes;

    @Column(name = "assigned_company")
    private String assignedCompany;

    @Column(name = "type")
    private String type;

    @Column(name = "status")
    private String status;

    @Column(name = "assigned_store")
    private String assignedStore;
}
