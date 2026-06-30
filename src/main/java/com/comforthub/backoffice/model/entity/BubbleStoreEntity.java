package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "bubble_stores") @Data @NoArgsConstructor @AllArgsConstructor
public class BubbleStoreEntity {
    @Id @Column(name = "id", nullable = false) private String id;
    @Column(name = "name") private String name;
    @Column(name = "company") private String company;
    @Column(name = "availability_id") private String availabilityId;
    @Column(name = "is_deleted") private Boolean isDeleted;
}
