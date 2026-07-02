package com.comforthub.backoffice.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Consumer-API bean wiring. Currently only the delivery-fee pricing policy,
 * exposed as a replaceable bean because the pricing model is a pending
 * business decision (see {@link DeliveryFeePolicy}).
 */
@Configuration
public class ConsumerConfig {

    /** Default pricing: €5 base + €1/km — Bubble's current hardcoded formula. */
    @Bean
    @ConditionalOnMissingBean(DeliveryFeePolicy.class)
    public DeliveryFeePolicy deliveryFeePolicy(
            @Value("${consumer.delivery-fee.base-eur:5.0}") BigDecimal baseEur,
            @Value("${consumer.delivery-fee.per-km-eur:1.0}") BigDecimal perKmEur) {
        return new BasePlusPerKmDeliveryFeePolicy(baseEur, perKmEur);
    }
}
