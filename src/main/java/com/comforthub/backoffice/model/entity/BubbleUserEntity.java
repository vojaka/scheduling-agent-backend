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
}
