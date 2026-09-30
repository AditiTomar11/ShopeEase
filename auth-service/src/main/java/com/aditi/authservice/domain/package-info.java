/**
 * DOMAIN LAYER — the innermost ring of the onion.
 *
 * <p>Rules enforced here (and reviewable, because nothing outside this package
 * may violate them):
 * <ol>
 *   <li>No Spring annotations ({@code @Service}, {@code @Component}, …).</li>
 *   <li>No persistence annotations ({@code @Entity}, {@code @Table}, …).</li>
 *   <li>No web types ({@code @RestController}, {@code HttpServletRequest}, …).</li>
 *   <li>No third-party SDK types (no JJWT, no AWS, no Jackson).</li>
 *   <li>Only depends on the JDK and on other {@code domain} classes.</li>
 * </ol>
 *
 * <p>Everything the business needs from the outside world is expressed as a
 * <em>port</em> — an interface declared right here and implemented outside.
 * That is the dependency rule: arrows point inwards, always.
 */
package com.aditi.authservice.domain;
