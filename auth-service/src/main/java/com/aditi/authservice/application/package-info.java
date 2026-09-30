/**
 * APPLICATION LAYER — use cases.
 *
 * <p>Allowed to depend on: {@code domain} and the JDK.
 * Forbidden to depend on: {@code infrastructure}, {@code presentation},
 * Spring Web, JPA, or any vendor SDK.
 *
 * <p>{@code @Service} and {@code @Transactional} do appear here. That is not a
 * leak of infrastructure in the strict sense — they are declarative markers
 * that the container reads, and the class itself stays free of framework types.
 * The test that the rule holds: this package still compiles and runs if you
 * delete every Spring annotation from it.
 */
package com.aditi.authservice.application;
