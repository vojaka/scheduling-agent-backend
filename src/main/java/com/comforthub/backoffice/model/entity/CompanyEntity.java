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

@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name")
    private String name;

    @Column(name = "reg_code")
    private String regCode;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "owners", columnDefinition = "text[]")
    private String[] owners;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "workers", columnDefinition = "text[]")
    private String[] workers;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    public CompanyEntity(String id, String name, String regCode, String[] owners, String[] workers) {
        this.id = id;
        this.name = name;
        this.regCode = regCode;
        this.owners = owners;
        this.workers = workers;
        this.isDeleted = false;
    }
}
