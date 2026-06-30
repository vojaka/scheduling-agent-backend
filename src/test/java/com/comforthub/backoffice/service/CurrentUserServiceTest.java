package com.comforthub.backoffice.service;

import com.comforthub.backoffice.exception.ForbiddenException;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.model.entity.CompanyEntity;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the role-resolution logic that gates OWNER-only actions.
 *
 * <p>Scoping/role model: the JWT {@code sub} resolves to a synced user via
 * {@code auth0_user_id}; that user's {@code bubble_id} is matched against the
 * company's {@code owners[]} / {@code workers[]} arrays. Anything unresolved
 * (no user, no company, or membership in neither list) is treated as
 * {@code NONE} — a non-owner — so OWNER-only actions fail safe.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private BubbleUserRepository userRepository;

    @Mock
    private CompanyRepository companyRepository;

    private CurrentUserService service() {
        return new CurrentUserService(userRepository, companyRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String sub) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sub)
                .claim("sub", sub)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private BubbleUserEntity user(String bubbleId, String companyId) {
        BubbleUserEntity u = new BubbleUserEntity();
        u.setBubbleId(bubbleId);
        u.setCompanyId(companyId);
        return u;
    }

    private CompanyEntity company(String id, String[] owners, String[] workers) {
        CompanyEntity c = new CompanyEntity();
        c.setId(id);
        c.setOwners(owners);
        c.setWorkers(workers);
        return c;
    }

    @Test
    void ownerResolvesToOwnerRole_andRequireOwnerPasses() {
        authenticateAs("auth0|owner");
        when(userRepository.findByAuth0UserId("auth0|owner"))
                .thenReturn(Optional.of(user("u-owner", "c1")));
        when(companyRepository.findById("c1"))
                .thenReturn(Optional.of(company("c1", new String[]{"u-owner"}, new String[]{"u-worker"})));

        CurrentUserService svc = service();

        assertThat(svc.currentRole()).isEqualTo(CurrentUserService.Role.OWNER);
        assertThat(svc.isOwner()).isTrue();
        assertThatCode(svc::requireOwner).doesNotThrowAnyException();
    }

    @Test
    void workerResolvesToWorkerRole_andRequireOwnerForbids() {
        authenticateAs("auth0|worker");
        when(userRepository.findByAuth0UserId("auth0|worker"))
                .thenReturn(Optional.of(user("u-worker", "c1")));
        when(companyRepository.findById("c1"))
                .thenReturn(Optional.of(company("c1", new String[]{"u-owner"}, new String[]{"u-worker"})));

        CurrentUserService svc = service();

        assertThat(svc.currentRole()).isEqualTo(CurrentUserService.Role.WORKER);
        assertThat(svc.isOwner()).isFalse();
        assertThatThrownBy(svc::requireOwner).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unknownUserResolvesToNone_andRequireOwnerForbids_failSafe() {
        authenticateAs("auth0|stranger");
        when(userRepository.findByAuth0UserId("auth0|stranger")).thenReturn(Optional.empty());

        CurrentUserService svc = service();

        assertThat(svc.currentRole()).isEqualTo(CurrentUserService.Role.NONE);
        assertThat(svc.isOwner()).isFalse();
        assertThatThrownBy(svc::requireOwner).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userWithMissingCompanyResolvesToNone_andRequireOwnerForbids_failSafe() {
        authenticateAs("auth0|orphan");
        when(userRepository.findByAuth0UserId("auth0|orphan"))
                .thenReturn(Optional.of(user("u-orphan", "c-missing")));
        when(companyRepository.findById("c-missing")).thenReturn(Optional.empty());

        CurrentUserService svc = service();

        assertThat(svc.currentRole()).isEqualTo(CurrentUserService.Role.NONE);
        assertThatThrownBy(svc::requireOwner).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userInNeitherListResolvesToNone_andRequireOwnerForbids_failSafe() {
        authenticateAs("auth0|bystander");
        when(userRepository.findByAuth0UserId("auth0|bystander"))
                .thenReturn(Optional.of(user("u-bystander", "c1")));
        when(companyRepository.findById("c1"))
                .thenReturn(Optional.of(company("c1", new String[]{"u-owner"}, new String[]{"u-worker"})));

        CurrentUserService svc = service();

        assertThat(svc.currentRole()).isEqualTo(CurrentUserService.Role.NONE);
        assertThatThrownBy(svc::requireOwner).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unauthenticatedResolvesToNone_andRequireOwnerForbids_failSafe() {
        // No authentication in the security context.
        CurrentUserService svc = service();

        assertThat(svc.currentSub()).isNull();
        assertThat(svc.currentRole()).isEqualTo(CurrentUserService.Role.NONE);
        assertThatThrownBy(svc::requireOwner).isInstanceOf(ForbiddenException.class);
    }
}
