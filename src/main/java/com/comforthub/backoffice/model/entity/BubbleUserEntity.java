package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class BubbleUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "bubble_id", unique = true)
    private String bubbleId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "role")
    private String role;

    @Column(name = "max_hours")
    private BigDecimal maxHours;

    @Column(name = "is_active")
    private Boolean isActive;

    // --- user scoping (V2__add_user_scoping.sql) ---
    /** Auth0 `sub` claim; maps the JWT principal to this user. Set out-of-band, not from Bubble sync. */
    @Column(name = "auth0_user_id")
    private String auth0UserId;

    /** The company this user represents — the key every scoped query filters on. */
    @Column(name = "company_id")
    private String companyId;
}
