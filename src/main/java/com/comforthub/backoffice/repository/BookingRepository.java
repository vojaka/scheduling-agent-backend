package com.comforthub.backoffice.repository;

import com.comforthub.backoffice.model.entity.BookingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    /**
     * List bookings for a company, optionally filtered by worker and/or date range.
     * Returns all bookings whose window overlaps [from, to].
     */
    @Query("SELECT b FROM BookingEntity b WHERE b.companyId = :companyId" +
           " AND (:workerId IS NULL OR b.workerId = :workerId)" +
           " AND (:from IS NULL OR b.endTime >= :from)" +
           " AND (:to IS NULL OR b.startTime <= :to)" +
           " ORDER BY b.startTime ASC")
    Page<BookingEntity> findByCompanyFiltered(@Param("companyId") String companyId,
                                              @Param("workerId") UUID workerId,
                                              @Param("from") OffsetDateTime from,
                                              @Param("to") OffsetDateTime to,
                                              Pageable pageable);
}
