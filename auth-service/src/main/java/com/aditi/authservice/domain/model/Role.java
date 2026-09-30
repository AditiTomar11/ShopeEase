package com.aditi.authservice.domain.model;

/**
 * Roles the domain understands.
 *
 * <p>Kept as a real enum instead of a raw {@code String} so that a typo like
 * {@code "ADMN"} becomes a compile error / fails fast instead of silently
 * creating an account that can never reach the admin panel.
 *
 * <p>Persistence note: the JPA entity maps this with
 * {@code @Enumerated(EnumType.STRING)}, so the stored column value is the
 * enum <em>name</em> — exactly what earlier revisions of this service wrote
 * ("ADMIN" / "CUSTOMER"), so existing rows keep working.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
