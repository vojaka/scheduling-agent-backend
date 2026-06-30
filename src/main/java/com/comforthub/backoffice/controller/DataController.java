package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoints exposing the synced PostgreSQL data to the backoffice.
 * Secured by the JWT resource server (see SecurityConfig) and scoped to the
 * authenticated user's company (see CurrentUserService). A caller with no
 * resolvable company sees an empty list rather than everyone's data.
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
    public List<BubbleShiftEntity> getShifts() {
        return currentUserService.currentCompanyId()
                .map(shiftRepository::findByAssignedCompany)
                .orElseGet(List::of);
    }

    @GetMapping("/users")
    public List<BubbleUserEntity> getUsers() {
        return currentUserService.currentCompanyId()
                .map(userRepository::findByCompanyId)
                .orElseGet(List::of);
    }

    @GetMapping("/stores")
    public List<BubbleStoreEntity> getStores() {
        return currentUserService.currentCompanyId()
                .map(storeRepository::findByCompany)
                .orElseGet(List::of);
    }
}
