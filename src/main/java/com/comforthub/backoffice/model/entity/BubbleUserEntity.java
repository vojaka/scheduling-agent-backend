package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "bubble_users") @Data @NoArgsConstructor @AllArgsConstructor
public class BubbleUserEntity {
    @Id @Column(name = "id", nullable = false) private String id;
    @Column(name = "name") private String name;
    @Column(name = "role") private String role;
    @Column(name = "max_hours") private Integer maxHours;
    @Column(name = "active") private Boolean active;

    // --- user_id scoping (V2__add_user_scoping.sql) ---
    /** Auth0 `sub` claim; maps the JWT principal to this user. Set out-of-band, not from Bubble sync. */
    @Column(name = "auth0_user_id") private String auth0UserId;
    /** The company this user represents — the key every scoped query filters on. */
    @Column(name = "company_id") private String companyId;
}
