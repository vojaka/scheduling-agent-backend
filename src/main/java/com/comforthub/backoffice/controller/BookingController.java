package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.BookingEntity;
import com.comforthub.backoffice.repository.BookingRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bookings CRUD — calendar events tied to workers/stores.
 * All reads/writes scoped to the caller's company.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final CurrentUserService currentUserService;

    public BookingController(BookingRepository bookingRepository,
                             CurrentUserService currentUserService) {
        this.bookingRepository = bookingRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * List bookings for the company.
     * Optional params: workerId, from (ISO-8601), to (ISO-8601).
     * Returns bookings whose window overlaps [from, to].
     */
    @GetMapping
    public Page<BookingEntity> getBookings(
            @RequestParam(required = false) UUID workerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Pageable pageable) {
        return currentUserService.currentCompanyId()
                .map(companyId -> bookingRepository.findByCompanyFiltered(
                        companyId, workerId, from, to, pageable))
                .orElseGet(Page::empty);
    }

    @PostMapping
    public ResponseEntity<BookingEntity> createBooking(@RequestBody BookingEntity body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    body.setId(null);
                    body.setCompanyId(companyId);
                    return ResponseEntity.ok(bookingRepository.save(body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookingEntity> updateBooking(@PathVariable UUID id,
                                                       @RequestBody BookingEntity body) {
        return currentUserService.currentCompanyId()
                .flatMap(companyId -> bookingRepository.findById(id)
                        .filter(b -> companyId.equals(b.getCompanyId())))
                .map(existing -> {
                    if (body.getTitle() != null)         existing.setTitle(body.getTitle());
                    if (body.getStartTime() != null)     existing.setStartTime(body.getStartTime());
                    if (body.getEndTime() != null)       existing.setEndTime(body.getEndTime());
                    if (body.getWorkerId() != null)      existing.setWorkerId(body.getWorkerId());
                    if (body.getStoreId() != null)       existing.setStoreId(body.getStoreId());
                    if (body.getCustomerName() != null)  existing.setCustomerName(body.getCustomerName());
                    if (body.getCustomerEmail() != null) existing.setCustomerEmail(body.getCustomerEmail());
                    return ResponseEntity.ok(bookingRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBooking(@PathVariable UUID id) {
        var existing = currentUserService.currentCompanyId()
                .flatMap(companyId -> bookingRepository.findById(id)
                        .filter(b -> companyId.equals(b.getCompanyId())));
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        bookingRepository.delete(existing.get());
        return ResponseEntity.ok().build();
    }
}
