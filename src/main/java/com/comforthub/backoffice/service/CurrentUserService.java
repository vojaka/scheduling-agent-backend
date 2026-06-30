package com.comforthub.backoffice.service;

import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the authenticated principal (Auth0 JWT `sub`) to the synced
 * Bubble user and that user's company — the key every scoped query filters
 * on. See V2__add_user_scoping.sql.
 *
 * Data boundary is by company (owners and workers of the same company see
 * the same rows). The owner-vs-worker RIGHTS distinction is a separate
 * authorization concern and is not enforced here yet.
 */
@Service
public class CurrentUserService {

    private final BubbleUserRepository userRepository;

    public CurrentUserService(BubbleUserRepository userRepository) {
        this.userRepository = userRepository;
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
}
