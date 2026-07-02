package com.comforthub.backoffice.consumer;

import com.comforthub.backoffice.client.BubbleClient;
import com.comforthub.backoffice.consumer.dto.AddCartItemRequest;
import com.comforthub.backoffice.consumer.dto.CartDto;
import com.comforthub.backoffice.consumer.dto.CartItemDto;
import com.comforthub.backoffice.consumer.dto.UpdateCartItemRequest;
import com.comforthub.backoffice.consumer.mapper.CartItemBubbleMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * The consumer's cart — scoped to the authenticated user's Bubble cart
 * ({@code user."Cart (single)"}).
 *
 * <p>Reads proxy the Data API; mutations proxy the Bubble cart <b>workflows</b>
 * so all of Bubble's cart machinery keeps running server-side: stock
 * reservation and auto-expiry, line merge/dedupe, two-phase pricing, order
 * attachment and payment-nonce scheduling (see add-to-cart-discovery.md). This
 * API never recomputes prices or touches stock itself.
 *
 * <p>Quantity changes ({@code PATCH}) have no documented single workflow: the
 * line's {@code Quantity} is patched via the Data API and Bubble's
 * {@code cart_item_recalc} workflow is invoked to roll the documented
 * unit-cost fields up to line totals.
 * TODO(verify vs version-test): confirm {@code cart_item_recalc}'s parameter
 * shape, and whether quantity changes must also re-reserve stock (the Bubble
 * front-end adds/removes lines rather than editing quantities in place).
 */
@RestController
@RequestMapping("/api/consumer/cart")
public class ConsumerCartController {

    private static final int BUBBLE_MAX_LIMIT = 100;

    private final BubbleClient bubbleClient;
    private final CartItemBubbleMapper mapper;
    private final ConsumerUserService consumerUserService;

    public ConsumerCartController(BubbleClient bubbleClient,
                                  CartItemBubbleMapper mapper,
                                  ConsumerUserService consumerUserService) {
        this.bubbleClient = bubbleClient;
        this.mapper = mapper;
        this.consumerUserService = consumerUserService;
    }

    /** The user's cart with its active lines. Empty cart when the user has none yet. */
    @GetMapping
    public ResponseEntity<CartDto> getCart() {
        if (consumerUserService.currentBubbleUserId().isEmpty()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(loadCart());
    }

    /**
     * Add an item — proxies {@code adding_to_cart_attributes(be)}. Bubble
     * decides create-vs-merge, reserves stock (409 semantics surface as a
     * Bubble error), prices the line and attaches it to an order. Responds
     * with the refreshed cart.
     */
    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(@RequestBody AddCartItemRequest body) {
        Optional<String> userIdOpt = consumerUserService.currentBubbleUserId();
        if (userIdOpt.isEmpty()) {
            return ResponseEntity.status(403).build();
        }
        if (isBlank(body.getInventoryId()) || isBlank(body.getOfferingId())
                || (body.getQuantity() != null && body.getQuantity() < 1)) {
            return ResponseEntity.badRequest().build();
        }
        bubbleClient.runWorkflow(CartItemBubbleMapper.WF_ADD_TO_CART,
                mapper.addToCartParams(body, userIdOpt.get()));
        return ResponseEntity.ok(loadCart());
    }

    /** Change a line's quantity. 404 when the line is not in the user's cart. */
    @PatchMapping("/items/{id}")
    public ResponseEntity<CartItemDto> updateItem(@PathVariable String id,
                                                  @RequestBody UpdateCartItemRequest body) {
        if (body.getQuantity() == null || body.getQuantity() < 1) {
            return ResponseEntity.badRequest().build();
        }
        if (!ownedByCurrentUser(id)) {
            return ResponseEntity.notFound().build();
        }
        bubbleClient.update(CartItemBubbleMapper.TYPE, id,
                mapper.quantityUpdateBody(body.getQuantity()));
        // Let Bubble roll unit costs up to line totals.
        // TODO(verify vs version-test): cart_item_recalc parameter shape.
        bubbleClient.runWorkflow(CartItemBubbleMapper.WF_CART_ITEM_RECALC,
                mapper.recalcParams(id));
        Map<String, Object> reloaded = bubbleClient.get(CartItemBubbleMapper.TYPE, id);
        return reloaded != null
                ? ResponseEntity.ok(mapper.toDto(reloaded))
                : ResponseEntity.notFound().build();
    }

    /**
     * Remove a line — proxies {@code delete_cart_item}, which reverts reserved
     * stock and cleans up the parent order (last-item handling included).
     * 404 when the line is not in the user's cart.
     */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable String id) {
        if (!ownedByCurrentUser(id)) {
            return ResponseEntity.notFound().build();
        }
        bubbleClient.runWorkflow(CartItemBubbleMapper.WF_DELETE_CART_ITEM,
                mapper.deleteCartItemParams(id));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------- helpers

    /** True if cart item {@code id} exists, is active, and sits in the user's cart. */
    private boolean ownedByCurrentUser(String id) {
        Optional<String> cartId = consumerUserService.currentCartId();
        if (cartId.isEmpty()) {
            return false;
        }
        Map<String, Object> record = bubbleClient.get(CartItemBubbleMapper.TYPE, id);
        return record != null
                && !mapper.isDeleted(record)
                && cartId.get().equals(mapper.cartOf(record));
    }

    /** The user's cart id + active lines + Bubble-computed total. */
    private CartDto loadCart() {
        CartDto cart = new CartDto();
        Optional<String> cartIdOpt = consumerUserService.currentCartId();
        if (cartIdOpt.isEmpty()) {
            return cart; // no cart yet — Bubble creates it during signup/add-to-cart
        }
        cart.setCartId(cartIdOpt.get());
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> r : bubbleClient.list(
                CartItemBubbleMapper.TYPE,
                mapper.activeItemsOfCart(cartIdOpt.get()),
                0, BUBBLE_MAX_LIMIT).getResults()) {
            CartItemDto item = mapper.toDto(r);
            cart.getItems().add(item);
            if (item.getTotalWithVat() != null) {
                total = total.add(item.getTotalWithVat());
            }
        }
        cart.setTotalWithVat(total);
        return cart;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
