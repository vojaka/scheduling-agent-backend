package com.comforthub.backoffice.payment.spi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the logging no-op {@link PaymentRecorder} only when no real
 * implementation is present. Add a {@code @Component} implementing
 * {@link PaymentRecorder} (the JPA-backed domain persistence, issue #83) and it
 * takes over automatically.
 */
@Configuration
public class PaymentRecorderConfig {

    @Bean
    @ConditionalOnMissingBean(PaymentRecorder.class)
    public PaymentRecorder loggingPaymentRecorder() {
        return new LoggingPaymentRecorder();
    }
}
