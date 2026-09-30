/**
 * INFRASTRUCTURE LAYER — the outermost ring.
 *
 * <ul>
 *   <li>{@code client} — the Feign declaration and its adapter to the domain port</li>
 *   <li>{@code persistence} — JPA entity, Spring Data, repository adapter</li>
 *   <li>{@code config} — composition root, Feign tuning, CORS, servlet filters</li>
 *   <li>{@code logging} — correlation id and request logging</li>
 * </ul>
 */
package com.aditi.order_service.infrastructure;
