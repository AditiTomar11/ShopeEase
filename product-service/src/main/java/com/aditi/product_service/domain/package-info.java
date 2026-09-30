/**
 * DOMAIN LAYER — the innermost ring.
 *
 * <p>Contains no Spring, no JPA, no Jakarta Servlet, no AWS SDK. The only
 * types from outside the JDK that appear here are the two ports the
 * application needs expressed as intentions:
 * <ul>
 *   <li>{@code ProductRepository} — "I need to load and save products"</li>
 *   <li>{@code FileStorage} — "I need to put bytes somewhere retrievable"</li>
 * </ul>
 * Everything else is expressed in the same terms by a layer further out.
 */
package com.aditi.product_service.domain;
