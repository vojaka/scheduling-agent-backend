package com.comforthub.backoffice.payment;

import com.comforthub.backoffice.payment.provider.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link PaymentProvider} by key and asserts capability support.
 * Providers self-register via Spring by implementing the capability interfaces.
 */
@Component
public class PaymentProviderRegistry {

    private final Map<ProviderKey, PaymentProvider> providers = new EnumMap<>(ProviderKey.class);

    public PaymentProviderRegistry(List<PaymentProvider> providerBeans) {
        for (PaymentProvider p : providerBeans) {
            providers.put(p.key(), p);
        }
    }

    public PaymentProvider get(ProviderKey key) {
        PaymentProvider p = providers.get(key);
        if (p == null) {
            throw new PaymentException("No payment provider registered for " + key);
        }
        return p;
    }

    /**
     * Resolve a provider and assert it implements the capability interface.
     *
     * @throws UnsupportedPaymentOperationException if the provider lacks it.
     */
    public <T extends PaymentProvider> T require(ProviderKey key, Class<T> capabilityType,
                                                 PaymentCapability capability) {
        PaymentProvider p = get(key);
        if (!capabilityType.isInstance(p)) {
            throw new UnsupportedPaymentOperationException(key, capability);
        }
        return capabilityType.cast(p);
    }

    public List<PaymentProvider> providersSupporting(PaymentCapability capability) {
        List<PaymentProvider> result = new ArrayList<>();
        for (PaymentProvider p : providers.values()) {
            if (p.capabilities().contains(capability)) {
                result.add(p);
            }
        }
        return result;
    }

    public boolean isRegistered(ProviderKey key) {
        return providers.containsKey(key);
    }
}
