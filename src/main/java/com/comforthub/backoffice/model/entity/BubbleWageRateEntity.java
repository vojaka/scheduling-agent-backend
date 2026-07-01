package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wage_rates")
@Data
@NoArgsConstructor
public class BubbleWageRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "bubble_id", unique = true)
    private String bubbleId;

    @Column(name = "company")
    private String company;

    @Column(name = "rate")
    @JdbcTypeCode(SqlTypes.DOUBLE)
    private BigDecimal rate;

    @Column(name = "user_id")
    private String userId;
}
