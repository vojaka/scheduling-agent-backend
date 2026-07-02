package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.consumer.dto.ConsumerAddressDto;
import com.comforthub.backoffice.consumer.dto.SaveAddressRequest;
import com.comforthub.backoffice.consumer.mapper.AddressBubbleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The consumer's delivery addresses — scoped to the authenticated user
 * ({@code address."Owner (Individual)"}).
 *
 * <p>Creates proxy Bubble's {@code save_address} workflow (validation chain,
 * primary-flag handover, user linking, audit log); deletes proxy
 * {@code delete_address} (soft delete + primary promotion). Reads and updates
 * go through the Data API — Bubble has no documented address <i>update</i>
 * workflow (see {@link AddressBubbleMapper#toUpdateBody}).
 */
@RestController
@RequestMapping("/api/consumer/addresses")
public class ConsumerAddressController {

    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final AddressBubbleMapper mapper;
    private final ConsumerUserService consumerUserService;

    public ConsumerAddressController(BubbleClient bubbleClient,
                                     AddressBubbleMapper mapper,
                                     ConsumerUserService consumerUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.consumerUserService = consumerUserService;
    }

    /** The user's active (non-deleted) addresses. */
    @GetMapping
    public List<ConsumerAddressDto> getAddresses() {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty()) {
            return List.of();
        }
        List<ConsumerAddressDto> out = new ArrayList<>();
        for (Map<String, Object> r : bubbleClient.list(
                AddressBubbleMapper.TYPE,
                mapper.activeAddressesOfUser(userIdOpt.get()),
                0, BUBBLE_MAX_LIMIT).getResults()) {
            out.add(mapper.toDto(r));
        }
        return out;
    }

    /**
     * Create an address via {@code save_address}. Bubble validates
     * (street/house/property-type/apartment chain) and returns its own error
     * payload on failure; the fresh list position is returned by re-reading.
     */
    @PostMapping
    public ResponseEntity<List<ConsumerAddressDto>> createAddress(@RequestBody SaveAddressRequest body) {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(403).build();
        }
        bubbleClient.runWorkflow(AddressBubbleMapper.WF_SAVE_ADDRESS,
                mapper.saveAddressParams(body, userIdOpt.get()));
        // save_address does not return the new record id in a documented key —
        // respond with the refreshed address list instead.
        return ResponseEntity.ok(getAddresses());
    }

    /** Update an address (Data API PATCH). 404 when not the user's address. */
    @PutMapping("/{id}")
    public ResponseEntity<ConsumerAddressDto> updateAddress(@PathVariable String id,
                                                            @RequestBody SaveAddressRequest body) {
        if (!ownedByCurrentUser(id)) {
            return ResponseEntity.notFound().build();
        }
        bubbleClient.update(AddressBubbleMapper.TYPE, id, mapper.toUpdateBody(body));
        Map<String, Object> reloaded = bubbleClient.get(AddressBubbleMapper.TYPE, id);
        return reloaded != null
                ? ResponseEntity.ok(mapper.toDto(reloaded))
                : ResponseEntity.notFound().build();
    }

    /**
     * Delete an address via {@code delete_address} (soft delete; Bubble
     * promotes the next address to primary if needed). 404 when not the
     * user's address.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable String id) {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty() || !ownedByCurrentUser(id)) {
            return ResponseEntity.notFound().build();
        }
        bubbleClient.runWorkflow(AddressBubbleMapper.WF_DELETE_ADDRESS,
                mapper.deleteAddressParams(id, userIdOpt.get()));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------- helpers

    /** True if address {@code id} exists, is active, and belongs to the user. */
    private boolean ownedByCurrentUser(String id) {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty()) {
            return false;
        }
        Map<String, Object> record = bubbleClient.get(AddressBubbleMapper.TYPE, id);
        return record != null
                && !mapper.isDeleted(record)
                && userIdOpt.get().equals(mapper.ownerOf(record));
    }
}
