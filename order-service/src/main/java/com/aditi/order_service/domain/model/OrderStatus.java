package com.aditi.order_service.domain.model;

import java.util.Locale;

/**
 * Order lifecycle states.
 *
 * <p>A Java enum rather than the free-text {@code String} the column used to
 * hold. Three concrete benefits:
 * <ul>
 *   <li>the set of legal values is discoverable by reading this file, and it is
 *       validated at compile time everywhere it is used;</li>
 *   <li>the transition rules can live next to the states (see
 *       {@link #canTransitionTo});</li>
 *   <li>and a typo like "DELIVERD" becomes a startup/serialisation error
 *       instead of an order that silently never gets marked delivered.</li>
 * </ul>
 *
 * <p>Values persist to the database as their names, so existing rows remain
 * valid.
 */
public enum OrderStatus {

    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * The legal transitions.
     *
     * <p>A shipped order cannot go back to pending, and a delivered or
     * cancelled order is terminal. Encoding this in the domain means an
     * illegal update is rejected with a 409 no matter which endpoint asks for
     * it — the alternative is the rule being re-implemented (slightly
     * differently) in every controller, admin screen and integration.
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /** Lenient parse used when reading data written by an older version. */
    public static OrderStatus fromOrDefault(String raw, OrderStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }
}
