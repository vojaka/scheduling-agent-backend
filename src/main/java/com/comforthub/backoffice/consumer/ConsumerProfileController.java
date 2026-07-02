package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.consumer.dto.ConsumerProfileDto;
import com.comforthub.backoffice.consumer.dto.PhoneVerificationRequest;
import com.comforthub.backoffice.consumer.dto.UpdateProfileRequest;
import com.comforthub.backoffice.consumer.mapper.ConsumerProfileBubbleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * The authenticated consumer's profile.
 *
 * <p>Reads/updates go through the Data API on the user's own Bubble record;
 * Bubble's user DB triggers (full-name composition, phone-change-pending,
 * profile completeness) keep firing on writes. Phone verification proxies
 * Bubble's Twilio workflows — see {@link ConsumerProfileBubbleMapper} for the
 * confirm-code caveat.
 */
@RestController
@RequestMapping("/api/consumer/profile")
public class ConsumerProfileController {

    private final BubbleClient bubbleClient;
    private final ConsumerProfileBubbleMapper mapper;
    private final ConsumerUserService consumerUserService;

    public ConsumerProfileController(BubbleClient bubbleClient,
                                     ConsumerProfileBubbleMapper mapper,
                                     ConsumerUserService consumerUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.consumerUserService = consumerUserService;
    }

    /** The user's profile; 404 when no Bubble user carries this Auth0 sub. */
    @GetMapping
    public ResponseEntity<ConsumerProfileDto> getProfile() {
        return consumerUserService.currentBubbleUser()
                .map(u -> ResponseEntity.ok(mapper.toDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Update the self-editable fields; responds with the persisted profile. */
    @PatchMapping
    public ResponseEntity<ConsumerProfileDto> updateProfile(@RequestBody UpdateProfileRequest body) {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> update = mapper.toUpdateBody(body);
        if (update.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        bubbleClient.update(ConsumerProfileBubbleMapper.TYPE, userIdOpt.get(), update);
        Map<String, Object> reloaded = bubbleClient.get(ConsumerProfileBubbleMapper.TYPE, userIdOpt.get());
        return reloaded != null
                ? ResponseEntity.ok(mapper.toDto(reloaded))
                : ResponseEntity.notFound().build();
    }

    /**
     * Send an SMS verification code — proxies Bubble's {@code verification}
     * workflow (Twilio Send-Code). Bubble returns Twilio's raw response, which
     * is passed through unchanged (documented behaviour of the workflow).
     */
    @PostMapping("/verify-phone")
    public ResponseEntity<Map<String, Object>> verifyPhone(@RequestBody PhoneVerificationRequest body) {
        if (consumerUserService.currentBubbleUserId().isEmpty()) {
            return ResponseEntity.status(403).build();
        }
        if (isBlank(body.getPhoneNumber())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(bubbleClient.runWorkflow(
                ConsumerProfileBubbleMapper.WF_SEND_PHONE_CODE,
                mapper.sendCodeParams(body.getPhoneNumber())));
    }

    /**
     * Confirm the SMS code.
     * TODO(verify vs version-test): no confirm-code workflow exists in the
     * documented Bubble inventory (the Bubble front-end calls the Twilio
     * plugin's Check-Code action directly) — the workflow name/shape assumed
     * here must be created or confirmed in Bubble before go-live. Fails
     * loudly until then.
     */
    @PostMapping("/confirm-phone")
    public ResponseEntity<Map<String, Object>> confirmPhone(@RequestBody PhoneVerificationRequest body) {
        if (consumerUserService.currentBubbleUserId().isEmpty()) {
            return ResponseEntity.status(403).build();
        }
        if (isBlank(body.getPhoneNumber()) || isBlank(body.getCode())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(bubbleClient.runWorkflow(
                ConsumerProfileBubbleMapper.WF_CONFIRM_PHONE_CODE,
                mapper.confirmCodeParams(body.getPhoneNumber(), body.getCode())));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
