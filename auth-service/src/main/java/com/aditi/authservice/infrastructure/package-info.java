/**
 * INFRASTRUCTURE LAYER — the outermost ring.
 *
 * <p>Allowed to depend on: everything, including the domain.
 * Must never be depended <em>upon</em> by the domain or the application layer.
 *
 * <p>What lives here, and why each piece is here rather than in the domain:
 * <ul>
 *   <li>{@code persistence} — JPA entities and Spring Data: SQL-shaped concerns.</li>
 *   <li>{@code security} — BCrypt and JJWT: crypto libraries the domain must not know.</li>
 *   <li>{@code config} — the composition root, Spring Security, CORS, seeding.</li>
 *   <li>{@code logging} — correlation ids and request logs: cross-cutting plumbing.</li>
 * </ul>
 */
package com.aditi.authservice.infrastructure;
