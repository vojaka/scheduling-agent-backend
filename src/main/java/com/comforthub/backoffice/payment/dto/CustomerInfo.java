package com.comforthub.backoffice.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerInfo {
    private String name;
    private String email;
    /** BCP-47 locale, e.g. "et", "en". */
    private String locale;
}
