package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "offerings")
@Data
@NoArgsConstructor
public class OfferingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "bubble_id", unique = true)
    private String bubbleId;

    /** Scoping key — Bubble company text id. */
    @Column(name = "company_id")
    private String companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type")
    private String type;

    /** 'Active' | 'Draft' | 'Archive' | 'Inactive' */
    @Column(name = "status")
    private String status;

    @Column(name = "limited_visibility")
    private Boolean limitedVisibility;

    @Column(name = "unlimited_quantity")
    private Boolean unlimitedQuantity;

    @Column(name = "quantity_required")
    private Boolean quantityRequired;

    @Column(name = "delivery_type")
    private String deliveryType;

    /** Maps to PostgreSQL TEXT[]. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "pay_options", columnDefinition = "text[]")
    private String[] payOptions;

    @Column(name = "price_source")
    private String priceSource;

    @Column(name = "default_type")
    private String defaultType;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = "Active";
    }
}
