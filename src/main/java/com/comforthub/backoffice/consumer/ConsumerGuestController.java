package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.consumer.dto.GuestCheckoutRequest;
import com.comforthub.backoffice.consumer.dto.GuestCheckoutResponse;
import com.comforthub.backoffice.consumer.mapper.ConsumerProfileBubbleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Guest checkout — documented product decision: guests check out with an
 * email only, but a <b>shadow Bubble user record is still created</b> so the
 * cart/order machinery (which hangs everything off a user) keeps working. The
 * returned Bubble user id is what the app passes as the customer for
 * subsequent cart calls.
 *
 * <p>TODO(verify vs version-test): user creation via the Data API
 * ({@code POST /obj/user} with {@code {"email": ...}}) — confirm Bubble
 * accepts Data-API user creation on this app, or switch to a small Bubble
 * workflow wrapping "Create an account for someone else" (the primitive
 * {@code CREATE ACCOUNT} uses).
 *
 * <p>Note: sits under the JWT-protected {@code /api/**} umbrella like every
 * consumer endpoint. If true anonymous guests (no Auth0 session at all) are
 * required, this route needs a deliberate {@code permitAll} carve-out in
 * {@code SecurityConfig} — a security decision left explicit, not made here.
 */
@RestController
@RequestMapping("/api/consumer/guest")
public class ConsumerGuestController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final BubbleClient bubbleClient;

    public ConsumerGuestController(BubbleClient bubbleClient) {
        this.bubbleClient = bubbleClient;
    }

    /** Create the shadow user from an email; returns its Bubble id. */
    @PostMapping
    public ResponseEntity<GuestCheckoutResponse> createGuest(@RequestBody GuestCheckoutRequest body) {
        if (body.getEmail() == null || !EMAIL.matcher(body.getEmail().trim()).matches()) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("email", body.getEmail().trim());
        String userId = bubbleClient.create(ConsumerProfileBubbleMapper.TYPE, createBody);
        if (userId == null) {
            return ResponseEntity.status(502).build();
        }
        return ResponseEntity.ok(new GuestCheckoutResponse(userId));
    }
}
