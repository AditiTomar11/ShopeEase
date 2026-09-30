/**
 * INFRASTRUCTURE LAYER — the outermost ring.
 *
 * <ul>
 *   <li>{@code persistence} — JPA entities, Spring Data, repository adapters</li>
 *   <li>{@code storage} — S3 and local-disk implementations of the FileStorage port</li>
 *   <li>{@code config} — composition root, storage selection, CORS, filters</li>
 *   <li>{@code logging} — correlation id and request logging</li>
 * </ul>
 */
package com.aditi.product_service.infrastructure;
