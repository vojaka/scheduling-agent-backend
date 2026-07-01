package com.comforthub.backoffice.payment.spi;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.model.entity.DisputeEntity;
import com.comforthub.backoffice.model.entity.PaymentEntity;
import com.comforthub.backoffice.model.entity.PaymentEventEntity;
import com.comforthub.backoffice.model.entity.PaymentTokenEntity;
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
        PaymentEntity entity = new PaymentEntity();
        entity.setId(UUID.randomUUID());
        entity.setProvider(payment.provider().name());
        entity.setOperation(payment.operation().name());
        entity.setProviderRef(payment.providerRef());
        entity.setOrderId(payment.orderId());
        entity.setCompanyId(payment.companyId());
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
                String bubblePaymentStatus = null;
                if ("PAID".equals(payment.getStatus())) {
                    bubblePaymentStatus = "Paid";
                } else if ("CANCELLED".equals(payment.getStatus()) || "FAILED".equals(payment.getStatus())) {
                    bubblePaymentStatus = "Cancelled";
                }

                if (bubblePaymentStatus != null) {
                    try {
                        log.info("Writing back payment status '{}' to Bubble order '{}'", bubblePaymentStatus, payment.getOrderId());
                        bubbleClient.update("order", payment.getOrderId(), Map.of("S - Order Payment Status", bubblePaymentStatus));
                    } catch (Exception e) {
                        log.error("Failed to write back payment status to Bubble for order {}: {}", payment.getOrderId(), e.getMessage());
                    }
                }
            }
        }

        PaymentEventEntity entity = new PaymentEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setPaymentId(paymentId);
        entity.setProviderEventId(event.getProviderEventId());
        entity.setEventName(event.getOperation().name());
        entity.setStatus(event.getStatus().name());
        entity.setRawPayload(event.getRawPayload());
        entity.setCreatedAt(OffsetDateTime.now());
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
        }

        DisputeEntity entity = new DisputeEntity();
        entity.setId(UUID.randomUUID());
        entity.setPaymentId(paymentId);
        entity.setProviderRef(dispute.getProviderRef());
        entity.setProviderDisputeRef(dispute.getProviderDisputeRef());
        entity.setReason(dispute.getReason());
        entity.setAmountMinor(dispute.getAmountMinor());
        entity.setCurrency(dispute.getCurrency());
        entity.setCreatedAt(OffsetDateTime.now());
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
}
