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

/**
 * Synced Bubble company. owners/workers hold Bubble user ids
 * (matching bubble_users.id) and drive the owner-vs-worker rights check.
 */
@Entity
@Table(name = "bubble_companies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BubbleCompanyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name")
    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "owners", columnDefinition = "text[]")
    private String[] owners;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "workers", columnDefinition = "text[]")
    private String[] workers;
}
