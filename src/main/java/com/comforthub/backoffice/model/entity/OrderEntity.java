package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "bubble_id", unique = true)
    private String bubbleId;

    /** Scoping key — Bubble company text id. */
    @Column(name = "company_id")
    private String companyId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "order_nr", unique = true)
    private String orderNr;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "type")
    private String type;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    /** 'Paid' | 'Unpaid' | 'Partial' */
    @Column(name = "payment_status")
    private String paymentStatus;

    /**
     * Kanban column status.
     * Allowed: not_started, planned, preparation_in_progress,
     * ready_for_pickup, courier_assigned, completed
     */
    @Column(name = "status", nullable = false)
    private String status;

    /** FK → users.id — worker assigned to this order. */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "ready_by")
    private OffsetDateTime readyBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = "not_started";
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
