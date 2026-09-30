/**
 * PRESENTATION LAYER — the outer edge.
 *
 * <p>Allowed to depend on: {@code application} and {@code domain} (for DTOs and
 * domain exception types). Forbidden to depend on: {@code infrastructure}.
 *
 * <p>It is the only layer that may import Spring Web, Jakarta Servlet or Jackson
 * annotations. Swapping REST for GraphQL or gRPC means rewriting this package
 * and nothing below it — which is the concrete payoff of the layering, and the
 * best answer to "what does Onion Architecture actually buy you?".
 */
package com.aditi.authservice.presentation;
