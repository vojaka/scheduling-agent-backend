package com.comforthub.backoffice.payment.config;

import com.comforthub.backoffice.payment.ProviderKey;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Binds the {@code payments.*} configuration. */
@Component
@ConfigurationProperties(prefix = "payments")
@Data
public class PaymentProperties {

    private ProviderKey defaultProvider = ProviderKey.MONTONIO;
    private Montonio montonio = new Montonio();
    private Everypay everypay = new Everypay();

    @Data
    public static class Montonio {
        private String baseUrl = "https://sandbox-stargate.montonio.com/api";
        private String accessKey;
        private String secretKey;
        private List<String> enabledMethods = new ArrayList<>();
    }

    @Data
    public static class Everypay {
        private String baseUrl = "https://igw-demo.every-pay.com/api/v4";
        private String username;
        private String secret;
        private String accountName;
        private List<String> enabledMethods = new ArrayList<>();
    }
}
