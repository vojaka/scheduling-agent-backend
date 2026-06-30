package com.comforthub.backoffice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bubble_wage_rates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BubbleWageRateEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "company")
    private String company;

    @Column(name = "rate")
    private Double rate;

    @Column(name = "user_id")
    private String userId;
}
