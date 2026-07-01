package com.comforthub.backoffice.payment;

/** Card-on-file agreement type for tokenized (recurring) payments. */
public enum AgreementType {
    /** Fixed-schedule subscription charges. */
    RECURRING,
    /** On-demand merchant-initiated charges (no fixed schedule). */
    UNSCHEDULED
}
