package com.comforthub.backoffice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bubble_shifts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BubbleShiftEntity {
    @Id @Column(name = "id", nullable = false) private String id;
    @Column(name = "assigned_user") private String assignedUser;
    @Column(name = "start_time") private OffsetDateTime startTime;
    @Column(name = "end_time") private OffsetDateTime endTime;
    @Column(name = "notes") private String notes;
    @Column(name = "assigned_company") private String assignedCompany;
    @Column(name = "type") private String type;
    @Column(name = "status") private String status;
    @Column(name = "assigned_store") private String assignedStore;
}
