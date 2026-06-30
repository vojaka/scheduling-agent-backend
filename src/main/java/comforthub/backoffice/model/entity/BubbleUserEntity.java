package com.comforthub.backoffice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bubble_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BubbleUserEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name")
    private String name;

    @Column(name = "role")
    private String role;

    @Column(name = "max_hours")
    private Integer maxHours;

    @Column(name = "active")
    private Boolean active;
}
