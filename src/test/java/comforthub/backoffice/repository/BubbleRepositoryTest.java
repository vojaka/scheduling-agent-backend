package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BubbleAvailabilityEntity;
import com.comforthub.backoffice.model.entity.BubbleShiftEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the JPA entities map cleanly onto the Flyway-created schema (ddl-auto=validate must pass
 * against a real Postgres), exercising the trickier column types: text[] and timestamptz.
 * Requires Docker; runs in CI (ubuntu-latest) where Docker is available.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class BubbleRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // Let Flyway own the schema and Hibernate validate against it.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private BubbleAvailabilityRepository availabilityRepository;

    @Autowired
    private BubbleShiftRepository shiftRepository;

    @Test
    void availabilityArrayColumnRoundTrips() {
        String[] days = {"Monday", "Tuesday", "Wednesday"};
        BubbleAvailabilityEntity a = new BubbleAvailabilityEntity(
                "a1", "Store", "store-1", days, 8, 20, 10, 18);
        availabilityRepository.saveAndFlush(a);

        Optional<BubbleAvailabilityEntity> found = availabilityRepository.findById("a1");
        assertTrue(found.isPresent());
        assertArrayEquals(days, found.get().getAvailableDays());
        assertEquals(8, found.get().getWorkdayStartHour());
    }

    @Test
    void shiftTimestamptzRoundTrips() {
        OffsetDateTime start = OffsetDateTime.of(2026, 6, 29, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 6, 29, 16, 0, 0, 0, ZoneOffset.UTC);
        BubbleShiftEntity s = new BubbleShiftEntity(
                "s1", "u1", start, end, "Morning", "company-1", "Regular", "Approved", "store-1");
        shiftRepository.saveAndFlush(s);

        Optional<BubbleShiftEntity> found = shiftRepository.findById("s1");
        assertTrue(found.isPresent());
        assertEquals(start.toInstant(), found.get().getStartTime().toInstant());
        assertEquals("Regular", found.get().getType());
    }
}
