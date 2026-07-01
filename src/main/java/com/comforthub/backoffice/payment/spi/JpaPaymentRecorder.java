package com.comforthub.backoffice.payment.spi;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.model.entity.DisputeEntity;
import com.comforthub.backoffice.model.entity.PaymentEntity;
import com.comforthub.backoffice.model.entity.PaymentEventEntity;
import com.comforthub.backoffice.model.entity.PaymentTokenEntity;
import com.comforthub.backoffice.payment.PaymentOperation;
import com.comforthub.backoffice.payment.dto.DisputeEvent;
import com.comforthub.backoffice.payment.dto.PaymentEvent;
import com.comforthub.backoffice.repository.DisputeRepository;
import com.comforthub.backoffice.repository.PaymentEventRepository;
import com.comforthub.backoffice.repository.PaymentRepository;
import com.comforthub.backoffice.repository.PaymentTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Primary
public class JpaPaymentRecorder implements PaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(JpaPaymentRecorder.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository eventRepository;
    private final PaymentTokenRepository tokenRepository;
    private final DisputeRepository disputeRepository;
    private final BubbleClient bubbleClient;

    public JpaPaymentRecorder(PaymentRepository paymentRepository,
                              PaymentEventRepository eventRepository,
                              PaymentTokenRepository tokenRepository,
                              DisputeRepository disputeRepository,
                              BubbleClient bubbleClient) {
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.tokenRepository = tokenRepository;
        this.disputeRepository = disputeRepository;
        this.bubbleClient = bubbleClient;
    }

    @Override
    @Transactional
    public void recordInitiated(RecordedPayment payment) {
        log.info("Recording initiated payment for order={}, providerRef={}", payment.orderId(), payment.providerRef());

        String orderId = payment.orderId();
        String companyId = payment.companyId();
        UUID parentPaymentId = null;

        if (payment.parentProviderRef() != null) {
            Optional<PaymentEntity> parentOpt = paymentRepository.findByProviderRef(payment.parentProviderRef());
            if (parentOpt.isPresent()) {
                PaymentEntity parent = parentOpt.get();
                parentPaymentId = parent.getId();
                if (orderId == null) {
                    orderId = parent.getOrderId();
                }
                if (companyId == null) {
                    companyId = parent.getCompanyId();
                }
            } else {
                log.warn("Could not resolve parent payment for parentProviderRef={} (operation={})",
                        payment.parentProviderRef(), payment.operation());
            }
        }

        PaymentEntity entity = new PaymentEntity();
        entity.setId(UUID.randomUUID());
        entity.setProvider(payment.provider().name());
        entity.setOperation(payment.operation().name());
        entity.setProviderRef(payment.providerRef());
        entity.setOrderId(orderId);
        entity.setCompanyId(companyId);
        entity.setParentPaymentId(parentPaymentId);
        entity.setIsRecurring(payment.operation() == PaymentOperation.RECURRING_INIT
                || payment.operation() == PaymentOperation.RECURRING_CHARGE);
        entity.setAmountMinor(payment.amountMinor());
        entity.setCurrency(payment.currency());
        entity.setMethod(payment.method());
        entity.setTokenRef(payment.tokenRef());
        entity.setStatus("INITIATED");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        paymentRepository.save(entity);
    }

    @Override
    public boolean isDuplicateEvent(String providerEventId) {
        if (providerEventId == null || providerEventId.isBlank()) {
            return false;
        }
        return eventRepository.existsByProviderEventId(providerEventId);
    }

    @Override
    @Transactional
    public void recordEvent(PaymentEvent event) {
        log.info("Recording payment event: id={}, status={}", event.getProviderEventId(), event.getStatus());

        Optional<PaymentEntity> paymentOpt = paymentRepository.findByProviderRef(event.getProviderRef());
        UUID paymentId = null;
        if (paymentOpt.isPresent()) {
            PaymentEntity payment = paymentOpt.get();
            paymentId = payment.getId();
            payment.setStatus(event.getStatus().name());
            payment.setUpdatedAt(OffsetDateTime.now());
            paymentRepository.save(payment);

            if (payment.getOrderId() != null && !payment.getOrderId().isBlank()) {
                String bubblePaymentStatus = mapToBubblePaymentStatus(payment.getStatus());
                if (bubblePaymentStatus != null) {
                    writeBackToBubble(payment.getOrderId(), bubblePaymentStatus);
                }
            }
        }

        PaymentEventEntity entity = new PaymentEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setPaymentId(paymentId);
        entity.setProviderEventId(event.getProviderEventId());
        entity.setOperation(event.getOperation() != null ? event.getOperation().name() : null);
        // `type` currently mirrors `operation` — provider-specific raw webhook event
        // type strings (e.g. Montonio/EveryPay's own event-type field) aren't parsed
        // out of the payload yet; that's #84's dispute/webhook payload work.
        entity.setType(event.getOperation() != null ? event.getOperation().name() : null);
        entity.setStatus(event.getStatus().name());
        entity.setSignatureValid(event.isSignatureValid());
        entity.setRawPayload(event.getRawPayload());
        entity.setReceivedAt(OffsetDateTime.now());
        eventRepository.save(entity);
    }

    @Override
    @Transactional
    public void recordDispute(DisputeEvent dispute) {
        log.warn("Recording dispute: disputeRef={}, reason={}", dispute.getProviderDisputeRef(), dispute.getReason());

        Optional<PaymentEntity> paymentOpt = paymentRepository.findByProviderRef(dispute.getProviderRef());
        UUID paymentId = null;
        if (paymentOpt.isPresent()) {
            PaymentEntity payment = paymentOpt.get();
            paymentId = payment.getId();
            payment.setStatus("DISPUTED");
            payment.setUpdatedAt(OffsetDateTime.now());
            paymentRepository.save(payment);

            if (payment.getOrderId() != null && !payment.getOrderId().isBlank()) {
                // "flagged on chargeback" per #83 — INFERRED Bubble option-set value,
                // same caveat as mapToBubblePaymentStatus below.
                writeBackToBubble(payment.getOrderId(), "Disputed");
            }
        } else {
            log.warn("Dispute {} could not be linked to a payment (providerRef={} not found) — "
                            + "provider-specific dispute payload parsing is #84's scope",
                    dispute.getProviderDisputeRef(), dispute.getProviderRef());
        }

        DisputeEntity entity = new DisputeEntity();
        entity.setId(UUID.randomUUID());
        entity.setPaymentId(paymentId);
        entity.setProviderRef(dispute.getProviderRef());
        entity.setProvider(dispute.getProvider() != null ? dispute.getProvider().name() : null);
        entity.setProviderDisputeRef(dispute.getProviderDisputeRef());
        entity.setReason(dispute.getReason());
        entity.setAmountMinor(dispute.getAmountMinor() == null ? 0L : dispute.getAmountMinor());
        entity.setCurrency(dispute.getCurrency());
        entity.setStatus("OPEN");
        entity.setRespondBy(dispute.getRespondBy() == null
                ? null
                : OffsetDateTime.ofInstant(dispute.getRespondBy(), ZoneOffset.UTC));
        entity.setRawPayload(dispute.getRawPayload());
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        disputeRepository.save(entity);
    }

    @Override
    @Transactional
    public void recordToken(RecordedToken token) {
        log.info("Recording customer token: tokenRef={}", token.tokenRef());
        PaymentTokenEntity entity = new PaymentTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setProvider(token.provider().name());
        entity.setCompanyId(token.companyId());
        entity.setCustomerRef(token.customerRef());
        entity.setTokenRef(token.tokenRef());
        entity.setAgreementType(token.agreementType().name());
        entity.setCardBrand(token.cardBrand());
        entity.setMaskedPan(token.maskedPan());
        entity.setIsRevoked(false);
        entity.setCreatedAt(OffsetDateTime.now());
        tokenRepository.save(entity);
    }

    @Override
    @Transactional
    public void revokeToken(String tokenRef) {
        log.info("Revoking customer token: tokenRef={}", tokenRef);
        Optional<PaymentTokenEntity> tokenOpt = tokenRepository.findByTokenRef(tokenRef);
        if (tokenOpt.isPresent()) {
            PaymentTokenEntity token = tokenOpt.get();
            token.setIsRevoked(true);
            tokenRepository.save(token);
        }
    }

    /**
     * Maps our internal {@code payments.status} to Bubble's "S - Order Payment
     * Status" option set. PAID -&gt; "Paid" and FAILED/CANCELLED -&gt; "Cancelled"
     * predate this change (already deployed with the original V7 scaffold) and are
     * left as-is rather than re-guessed without evidence either way. "Partial" for
     * PARTIALLY_REFUNDED is taken verbatim from the #83 issue text. Every value
     * here remains INFERRED — none have been confirmed against a live Bubble
     * sandbox order in this session (no Bubble API access was available).
     * TODO(#84/#87): verify these option-set strings against a real sandbox order.
     */
    private String mapToBubblePaymentStatus(String internalStatus) {
        return switch (internalStatus) {
            case "PAID" -> "Paid";
            case "FAILED", "CANCELLED" -> "Cancelled";
            case "REFUNDED" -> "Refunded";
            case "PARTIALLY_REFUNDED" -> "Partial";
            case "CHARGEBACK", "DISPUTED" -> "Disputed";
            default -> null;
        };
    }

    private void writeBackToBubble(String orderId, String bubblePaymentStatus) {
        try {
            log.info("Writing back payment status '{}' to Bubble order '{}'", bubblePaymentStatus, orderId);
            bubbleClient.update("order", orderId, Map.of("S - Order Payment Status", bubblePaymentStatus));
        } catch (Exception e) {
            log.error("Failed to write back payment status to Bubble for order {}: {}", orderId, e.getMessage());
        }
    }
}
