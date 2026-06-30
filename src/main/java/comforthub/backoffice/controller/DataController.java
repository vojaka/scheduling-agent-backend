package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import com.comforthub.backoffice.model.entity.BubbleStoreEntity;
import com.comforthub.backoffice.model.entity.BubbleUserEntity;
import com.comforthub.backoffice.repository.BubbleShiftRepository;
import com.comforthub.backoffice.repository.BubbleStoreRepository;
import com.comforthub.backoffice.repository.BubbleUserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoints exposing the synced PostgreSQL data to the backoffice.
 * Secured by the JWT resource server (see SecurityConfig) — all paths under /api require a valid token.
 */
@RestController
@RequestMapping("/api")
public class DataController {

    private final BubbleShiftRepository shiftRepository;
    private final BubbleUserRepository userRepository;
    private final BubbleStoreRepository storeRepository;

    public DataController(BubbleShiftRepository shiftRepository,
                          BubbleUserRepository userRepository,
                          BubbleStoreRepository storeRepository) {
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
    }

    @GetMapping("/shifts")
    public List<BubbleShiftEntity> getShifts() {
        return shiftRepository.findAll();
    }

    @GetMapping("/users")
    public List<BubbleUserEntity> getUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/stores")
    public List<BubbleStoreEntity> getStores() {
        return storeRepository.findAll();
    }
}
