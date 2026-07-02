package com.comforthub.backoffice.consumer;

import java.math.BigDecimal;

/**
 * Strategy for quoting a delivery fee from a route distance.
 *
 * <p>Kept behind an interface because the pricing model is a pending business
 * decision — the default implementation mirrors what Bubble's
 * {@code calculate distance} workflow hardcodes today (€5 base + €1/km, see
 * add-to-cart-discovery.md §4.3), but zones, per-merchant pricing or free-over
 * thresholds may replace it. Swap the bean, keep the endpoint contract.
 */
public interface DeliveryFeePolicy {

    /**
     * Quote the delivery fee for a route of {@code distanceKm} kilometres.
     *
     * @param distanceKm route distance in km, {@code >= 0}
     * @return the fee in EUR (VAT-inclusive, matching Bubble's current model)
     */
    BigDecimal feeFor(double distanceKm);
}
