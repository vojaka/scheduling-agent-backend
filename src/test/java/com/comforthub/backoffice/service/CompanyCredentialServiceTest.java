package com.comforthub.backoffice.service;

import com.comforthub.backoffice.model.entity.CompanyCredentialEntity;
import com.comforthub.backoffice.repository.CompanyCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyCredentialServiceTest {

    @Mock
    private CompanyCredentialRepository repository;

    private CredentialEncryptionService encryptionService;
    private CompanyCredentialService service;

    @BeforeEach
    void setUp() {
        // Use a standard 32-byte key for testing
        encryptionService = new CredentialEncryptionService("test-key-32-bytes-long-12345678");
        service = new CompanyCredentialService(repository, encryptionService);
    }

    @Test
    void saveCredential_encryptsAndSaves() {
        String companyId = "c1";
        String provider = "MONTONIO";
        String keyName = "secret_key";
        String rawValue = "super-secret-value";

        when(repository.findByCompanyIdAndProviderAndKeyName(companyId, provider, keyName))
                .thenReturn(Optional.empty());

        service.saveCredential(companyId, provider, keyName, rawValue);

        ArgumentCaptor<CompanyCredentialEntity> captor = ArgumentCaptor.forClass(CompanyCredentialEntity.class);
        verify(repository).save(captor.capture());

        CompanyCredentialEntity saved = captor.getValue();
        assertThat(saved.getCompanyId()).isEqualTo(companyId);
        assertThat(saved.getProvider()).isEqualTo(provider);
        assertThat(saved.getKeyName()).isEqualTo(keyName);
        assertThat(saved.getEncryptedValue()).isNotEqualTo(rawValue);
        assertThat(saved.getNonce()).isNotEmpty();

        // Verify it can be decrypted back to the original value
        String decrypted = encryptionService.decrypt(saved.getEncryptedValue(), saved.getNonce());
        assertThat(decrypted).isEqualTo(rawValue);
    }

    @Test
    void getDecryptedCredential_retrievesAndDecrypts() {
        String companyId = "c1";
        String provider = "MONTONIO";
        String keyName = "secret_key";
        String rawValue = "super-secret-value";

        CredentialEncryptionService.EncryptedResult encrypted = encryptionService.encrypt(rawValue);
        CompanyCredentialEntity entity = new CompanyCredentialEntity();
        entity.setCompanyId(companyId);
        entity.setProvider(provider);
        entity.setKeyName(keyName);
        entity.setEncryptedValue(encrypted.getCipherText());
        entity.setNonce(encrypted.getNonce());

        when(repository.findByCompanyIdAndProviderAndKeyName(companyId, provider, keyName))
                .thenReturn(Optional.of(entity));

        Optional<String> decrypted = service.getDecryptedCredential(companyId, provider, keyName);
        assertThat(decrypted).isPresent().contains(rawValue);
    }

    @Test
    void getDecryptedCredentialsForProvider_returnsAllDecryptedInMap() {
        String companyId = "c1";
        String provider = "MONTONIO";

        CredentialEncryptionService.EncryptedResult enc1 = encryptionService.encrypt("val1");
        CompanyCredentialEntity e1 = new CompanyCredentialEntity();
        e1.setKeyName("key1");
        e1.setEncryptedValue(enc1.getCipherText());
        e1.setNonce(enc1.getNonce());

        CredentialEncryptionService.EncryptedResult enc2 = encryptionService.encrypt("val2");
        CompanyCredentialEntity e2 = new CompanyCredentialEntity();
        e2.setKeyName("key2");
        e2.setEncryptedValue(enc2.getCipherText());
        e2.setNonce(enc2.getNonce());

        when(repository.findByCompanyIdAndProvider(companyId, provider))
                .thenReturn(Arrays.asList(e1, e2));

        Map<String, String> decryptedMap = service.getDecryptedCredentialsForProvider(companyId, provider);
        assertThat(decryptedMap)
                .hasSize(2)
                .containsEntry("key1", "val1")
                .containsEntry("key2", "val2");
    }
}
