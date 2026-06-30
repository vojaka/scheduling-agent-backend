package com.comforthub.backoffice.service;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleCompanyRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the authenticated principal (Auth0 JWT `sub`) to the synced
 * Bubble user, that user's company (the data-scoping key), and their role
 * within that company (OWNER vs WORKER).
 *
 * Data boundary is by company — owners and workers of the same company see
 * the same rows. Role only gates extra OWNER actions (manage workers,
 * generate/commit schedules). See V2/V3 migrations.
 */
@Service
public class CurrentUserService {

    public enum Role { OWNER, WORKER, NONE }

    private final BubbleUserRepository userRepository;
    private final BubbleCompanyRepository companyRepository;

    public CurrentUserService(BubbleUserRepository userRepository,
                              BubbleCompanyRepository companyRepository) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    /** The Auth0 `sub` claim of the current request, or null if unauthenticated. */
    public String currentSub() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return null;
    }

    /** The current user's synced Bubble record, matched by auth0_user_id. */
    public Optional<BubbleUserEntity> currentUser() {
        String sub = currentSub();
        if (sub == null) {
            return Optional.empty();
        }
        return userRepository.findByAuth0UserId(sub);
    }

    /** The company id the current user represents — the scoping key. */
    public Optional<String> currentCompanyId() {
        return currentUser()
                .map(BubbleUserEntity::getCompanyId)
                .filter(c -> c != null && !c.isBlank());
    }

    /** Whether the current user owns or merely works at their represented company. */
    public Role currentRole() {
        Optional<BubbleUserEntity> user = currentUser();
        if (user.isEmpty()) {
            return Role.NONE;
        }
        String companyId = user.get().getCompanyId();
        String userId = user.get().getId();
        if (companyId == null || companyId.isBlank()) {
            return Role.NONE;
        }
        return companyRepository.findById(companyId).map(c -> {
            if (arrayContains(c.getOwners(), userId)) {
                return Role.OWNER;
            }
            if (arrayContains(c.getWorkers(), userId)) {
                return Role.WORKER;
            }
            return Role.NONE;
        }).orElse(Role.NONE);
    }

    public boolean isOwner() {
        return currentRole() == Role.OWNER;
    }

    private static boolean arrayContains(String[] arr, String value) {
        if (arr == null || value == null) {
            return false;
        }
        for (String s : arr) {
            if (value.equals(s)) {
                return true;
            }
        }
        return false;
    }
}
