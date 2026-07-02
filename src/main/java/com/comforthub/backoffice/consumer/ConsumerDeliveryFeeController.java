package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.consumer.dto.DeliveryFeeDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery-fee quote for the checkout screen.
 *
 * <p>Pricing sits behind {@link DeliveryFeePolicy} (default: Bubble's current
 * €5 base + €1/km formula) because the final model is a pending business
 * decision. Distance is supplied by the caller — route computation (MapBox in
 * Bubble today) stays out of scope here.
 */
@RestController
@RequestMapping("/api/consumer/delivery-fee")
public class ConsumerDeliveryFeeController {

    private final DeliveryFeePolicy policy;

    public ConsumerDeliveryFeeController(DeliveryFeePolicy policy) {
        this.policy = policy;
    }

    /** Quote the fee for a route of {@code distanceKm} km. 400 on negative distance. */
    @GetMapping
    public ResponseEntity<DeliveryFeeDto> getDeliveryFee(@RequestParam double distanceKm) {
        if (distanceKm < 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new DeliveryFeeDto(distanceKm, policy.feeFor(distanceKm), "EUR"));
    }
}
