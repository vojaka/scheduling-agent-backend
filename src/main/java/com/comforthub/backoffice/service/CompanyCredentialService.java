package com.comforthub.backoffice.service;

import com.comforthub.backoffice.model.entity.CompanyCredentialEntity;
import com.comforthub.backoffice.repository.CompanyCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CompanyCredentialService {

    private final CompanyCredentialRepository repository;
    private final CredentialEncryptionService encryptionService;

    public CompanyCredentialService(CompanyCredentialRepository repository, CredentialEncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    @Transactional
    public void saveCredential(String companyId, String provider, String keyName, String rawValue) {
        Optional<CompanyCredentialEntity> existingOpt = repository.findByCompanyIdAndProviderAndKeyName(companyId, provider, keyName);
        
        CredentialEncryptionService.EncryptedResult encryptedResult = encryptionService.encrypt(rawValue);

        CompanyCredentialEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setEncryptedValue(encryptedResult.getCipherText());
            entity.setNonce(encryptedResult.getNonce());
            entity.setUpdatedAt(OffsetDateTime.now());
        } else {
            entity = new CompanyCredentialEntity();
            entity.setId(UUID.randomUUID());
            entity.setCompanyId(companyId);
            entity.setProvider(provider.toUpperCase());
            entity.setKeyName(keyName.toLowerCase());
            entity.setEncryptedValue(encryptedResult.getCipherText());
            entity.setNonce(encryptedResult.getNonce());
            entity.setCreatedAt(OffsetDateTime.now());
            entity.setUpdatedAt(OffsetDateTime.now());
        }
        repository.save(entity);
    }

    public Optional<String> getDecryptedCredential(String companyId, String provider, String keyName) {
        return repository.findByCompanyIdAndProviderAndKeyName(companyId, provider.toUpperCase(), keyName.toLowerCase())
                .map(entity -> encryptionService.decrypt(entity.getEncryptedValue(), entity.getNonce()));
    }

    public Map<String, String> getDecryptedCredentialsForProvider(String companyId, String provider) {
        List<CompanyCredentialEntity> entities = repository.findByCompanyIdAndProvider(companyId, provider.toUpperCase());
        Map<String, String> credentials = new HashMap<>();
        for (CompanyCredentialEntity entity : entities) {
            String decrypted = encryptionService.decrypt(entity.getEncryptedValue(), entity.getNonce());
            credentials.put(entity.getKeyName(), decrypted);
        }
        return credentials;
    }

    public List<CompanyCredentialEntity> getCredentialsForCompany(String companyId) {
        return repository.findByCompanyId(companyId);
    }

    @Transactional
    public void deleteCredential(String companyId, String provider, String keyName) {
        repository.deleteByCompanyIdAndProviderAndKeyName(companyId, provider.toUpperCase(), keyName.toLowerCase());
    }
}
