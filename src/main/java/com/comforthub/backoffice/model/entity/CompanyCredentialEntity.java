package com.comforthub.backoffice.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_credentials")
@Data
@NoArgsConstructor
public class CompanyCredentialEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "key_name", nullable = false)
    private String keyName;

    @Column(name = "encrypted_value", nullable = false)
    private String encryptedValue;

    @Column(name = "nonce", nullable = false)
    private String nonce;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
