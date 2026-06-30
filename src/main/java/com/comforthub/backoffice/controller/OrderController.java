package com.comforthub.backoffice.controller;

import com.comforthub.backoffice.model.entity.OrderEntity;
import com.comforthub.backoffice.repository.OrderRepository;
import com.comforthub.backoffice.service.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orders CRUD + kanban status transition.
 * All reads/writes scoped to the caller's company.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Set<String> VALID_STATUSES = Set.of(
            "not_started", "planned", "preparation_in_progress",
            "ready_for_pickup", "courier_assigned", "completed"
    );

    private final OrderRepository orderRepository;
    private final CurrentUserService currentUserService;

    public OrderController(OrderRepository orderRepository,
                           CurrentUserService currentUserService) {
        this.orderRepository = orderRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * List orders for the company.
     * Optional params: storeId, assignedTo (worker UUID), orderNr (substring), customer (name substring).
     */
    @GetMapping
    public Page<OrderEntity> getOrders(@RequestParam(required = false) UUID storeId,
                                       @RequestParam(required = false) UUID assignedTo,
                                       @RequestParam(required = false) String orderNr,
                                       @RequestParam(required = false) String customer,
                                       Pageable pageable) {
        return currentUserService.currentCompanyId()
                .map(companyId -> orderRepository.findByCompanyFiltered(
                        companyId, storeId, assignedTo, orderNr, customer, pageable))
                .orElseGet(Page::empty);
    }

    @PostMapping
    public ResponseEntity<OrderEntity> createOrder(@RequestBody OrderEntity body) {
        return currentUserService.currentCompanyId()
                .map(companyId -> {
                    body.setId(null);
                    body.setCompanyId(companyId);
                    // Auto-generate orderNr if not supplied
                    if (body.getOrderNr() == null || body.getOrderNr().isBlank()) {
                        body.setOrderNr("#" + String.format("%06d",
                                System.currentTimeMillis() % 1_000_000));
                    }
                    return ResponseEntity.ok(orderRepository.save(body));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderEntity> updateOrder(@PathVariable UUID id,
                                                   @RequestBody OrderEntity body) {
        return currentUserService.currentCompanyId()
                .flatMap(companyId -> orderRepository.findById(id)
                        .filter(o -> companyId.equals(o.getCompanyId())))
                .map(existing -> {
                    if (body.getCustomerName() != null)  existing.setCustomerName(body.getCustomerName());
                    if (body.getStoreId() != null)       existing.setStoreId(body.getStoreId());
                    if (body.getType() != null)          existing.setType(body.getType());
                    if (body.getAmount() != null)        existing.setAmount(body.getAmount());
                    if (body.getPaymentStatus() != null) existing.setPaymentStatus(body.getPaymentStatus());
                    if (body.getAssignedTo() != null)    existing.setAssignedTo(body.getAssignedTo());
                    if (body.getReadyBy() != null)       existing.setReadyBy(body.getReadyBy());
                    if (body.getNotes() != null)         existing.setNotes(body.getNotes());
                    return ResponseEntity.ok(orderRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Move an order to a new kanban column.
     * Body: {@code { "status": "planned" }}
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderEntity> updateStatus(@PathVariable UUID id,
                                                    @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            return ResponseEntity.badRequest().build();
        }
        return currentUserService.currentCompanyId()
                .flatMap(companyId -> orderRepository.findById(id)
                        .filter(o -> companyId.equals(o.getCompanyId())))
                .map(existing -> {
                    existing.setStatus(newStatus);
                    return ResponseEntity.ok(orderRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
