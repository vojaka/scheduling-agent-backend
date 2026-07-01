package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.exception.ForbiddenException;
import com.comforthub.backoffice.model.entity.CompanyCredentialEntity;
import com.comforthub.backoffice.service.CompanyCredentialService;
import com.comforthub.backoffice.service.CredentialEncryptionService;
import com.comforthub.backoffice.service.CurrentUserService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for managing merchant/company credentials securely.
 * Authenticated via Auth0 JWT, scoped to the caller's company.
 * Only owners can write/delete credentials.
 */
@RestController
@RequestMapping("/api/companies/credentials")
public class CompanyCredentialController {

    private final CompanyCredentialService credentialService;
    private final CurrentUserService currentUserService;
    private final CredentialEncryptionService encryptionService;

    public CompanyCredentialController(CompanyCredentialService credentialService,
                                       CurrentUserService currentUserService,
                                       CredentialEncryptionService encryptionService) {
        this.credentialService = credentialService;
        this.currentUserService = currentUserService;
        this.encryptionService = encryptionService;
    }

    @GetMapping
    public ResponseEntity<List<CompanyCredentialResponse>> getCredentials() {
        String companyId = requireCompany();
        List<CompanyCredentialEntity> entities = credentialService.getCredentialsForCompany(companyId);
        List<CompanyCredentialResponse> response = entities.stream()
                .map(entity -> {
                    String decrypted = encryptionService.decrypt(entity.getEncryptedValue(), entity.getNonce());
                    String masked = maskValue(decrypted);
                    return new CompanyCredentialResponse(
                            entity.getId(),
                            entity.getProvider(),
                            entity.getKeyName(),
                            masked,
                            entity.getCreatedAt(),
                            entity.getUpdatedAt()
                    );
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Void> saveCredential(@RequestBody CompanyCredentialRequest request) {
        currentUserService.requireOwner();
        String companyId = requireCompany();
        credentialService.saveCredential(
                companyId,
                request.getProvider(),
                request.getKeyName(),
                request.getValue()
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteCredential(@RequestParam String provider, @RequestParam String keyName) {
        currentUserService.requireOwner();
        String companyId = requireCompany();
        credentialService.deleteCredential(companyId, provider, keyName);
        return ResponseEntity.noContent().build();
    }

    private String requireCompany() {
        return currentUserService.currentCompanyId()
                .orElseThrow(() -> new ForbiddenException("No company is associated with the authenticated user."));
    }

    private String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyCredentialResponse {
        private UUID id;
        private String provider;
        private String keyName;
        private String maskedValue;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyCredentialRequest {
        private String provider;
        private String keyName;
        private String value;
    }
}
