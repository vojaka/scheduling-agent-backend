package com.comforthub.backoffice.consumer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Default {@link DeliveryFeePolicy}: flat base + per-km rate, rounded to one
 * decimal — the exact formula Bubble's {@code calculate distance} workflow
 * uses today ({@code (distance × 1) + 5, rounded to 1}).
 *
 * <p>Config-friendly on purpose (pricing is a pending business decision):
 * <pre>
 *   consumer.delivery-fee.base-eur=5.0
 *   consumer.delivery-fee.per-km-eur=1.0
 * </pre>
 * A richer policy (zones, per-merchant rates) can replace this bean without
 * touching the endpoint — it is registered via {@link ConsumerConfig} only
 * when no other {@link DeliveryFeePolicy} bean exists.
 */
public class BasePlusPerKmDeliveryFeePolicy implements DeliveryFeePolicy {

    private final BigDecimal baseEur;
    private final BigDecimal perKmEur;

    public BasePlusPerKmDeliveryFeePolicy(BigDecimal baseEur, BigDecimal perKmEur) {
        this.baseEur = baseEur;
        this.perKmEur = perKmEur;
    }

    @Override
    public BigDecimal feeFor(double distanceKm) {
        if (distanceKm < 0) {
            throw new IllegalArgumentException("distanceKm must be >= 0");
        }
        return baseEur
                .add(perKmEur.multiply(BigDecimal.valueOf(distanceKm)))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
