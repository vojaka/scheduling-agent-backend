package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.client.BubbleClient.BubbleListResult;
import com.comforthub.backoffice.dto.OrderDto;
import com.comforthub.backoffice.mapper.OrderBubbleMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The consumer's own orders — read-only.
 *
 * <p>Same Bubble {@code order} proxying and DTO mapping as the backoffice
 * {@code OrderController}, but scoped by <b>customer</b> ({@code "Customer
 * (Individual)" = the authenticated Bubble user}) instead of merchant.
 * {@code status} carries the same six kanban progress keys the backoffice
 * uses ({@code not_started → … → completed}); order <i>creation</i> stays with
 * Bubble's cart/checkout workflows (an order is attached during add-to-cart),
 * so there are no write endpoints here.
 */
@RestController
@RequestMapping("/api/consumer/orders")
public class ConsumerOrderController {

    /** Bubble caps a single Data API page at 100 records. */
    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final OrderBubbleMapper mapper;
    private final ConsumerUserService consumerUserService;

    public ConsumerOrderController(BubbleClient bubbleClient,
                                   OrderBubbleMapper mapper,
                                   ConsumerUserService consumerUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.consumerUserService = consumerUserService;
    }

    /** The user's orders, newest first, in the Spring Data {@link Page} envelope. */
    @GetMapping
    public Page<OrderDto> getOrders(Pageable pageable) {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty()) {
            return Page.empty(pageable);
        }

        int limit = Math.min(pageable.getPageSize(), BUBBLE_MAX_LIMIT);
        int cursor = (int) pageable.getOffset();

        BubbleListResult result = bubbleClient.list(
                OrderBubbleMapper.TYPE,
                mapper.customerConstraints(userIdOpt.get()),
                cursor, limit,
                OrderBubbleMapper.SORT_CREATED_DATE, true);

        List<OrderDto> content = result.getResults().stream()
                .map(mapper::toDto)
                .toList();

        long total = (long) cursor + result.getCount() + result.getRemaining();
        return new PageImpl<>(content, pageable, total);
    }

    /** One of the user's orders; 404 when missing or owned by someone else. */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable String id) {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> record = bubbleClient.get(OrderBubbleMapper.TYPE, id);
        if (record == null || !userIdOpt.get().equals(mapper.customerOf(record))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapper.toDto(record));
    }
}
