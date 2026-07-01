package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.CompanyCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyCredentialRepository extends JpaRepository<CompanyCredentialEntity, UUID> {
    List<CompanyCredentialEntity> findByCompanyId(String companyId);
    List<CompanyCredentialEntity> findByCompanyIdAndProvider(String companyId, String provider);
    Optional<CompanyCredentialEntity> findByCompanyIdAndProviderAndKeyName(String companyId, String provider, String keyName);
    void deleteByCompanyIdAndProviderAndKeyName(String companyId, String provider, String keyName);
}
