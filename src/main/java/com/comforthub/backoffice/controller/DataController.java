package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints exposing the synced PostgreSQL data to the backoffice.
 * Secured by the JWT resource server (see SecurityConfig) and scoped to the
 * authenticated user's company (see CurrentUserService). A caller with no
 * resolvable company sees an empty list rather than everyone's data.
 * Supports offset-based pagination.
 */
@RestController
@RequestMapping("/api")
public class DataController {

    private final BubbleShiftRepository shiftRepository;
    private final BubbleUserRepository userRepository;
    private final BubbleStoreRepository storeRepository;
    private final CurrentUserService currentUserService;

    public DataController(BubbleShiftRepository shiftRepository,
                          BubbleUserRepository userRepository,
                          BubbleStoreRepository storeRepository,
                          CurrentUserService currentUserService) {
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/shifts")
    public Page<BubbleShiftEntity> getShifts(Pageable pageable) {
        return currentUserService.currentCompanyId()
                .map(companyId -> shiftRepository.findByAssignedCompany(companyId, pageable))
                .orElseGet(Page::empty);
    }

    @GetMapping("/users")
    public Page<BubbleUserEntity> getUsers(Pageable pageable) {
        return currentUserService.currentCompanyId()
                .map(companyId -> userRepository.findByCompanyId(companyId, pageable))
                .orElseGet(Page::empty);
    }

    @GetMapping("/stores")
    public Page<BubbleStoreEntity> getStores(Pageable pageable) {
        return currentUserService.currentCompanyId()
                .map(companyId -> storeRepository.findByCompany(companyId, pageable))
                .orElseGet(Page::empty);
    }
}
