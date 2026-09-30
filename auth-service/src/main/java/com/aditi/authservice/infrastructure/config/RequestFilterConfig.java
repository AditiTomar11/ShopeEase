package com.aditi.authservice.infrastructure.config;

import com.aditi.authservice.infrastructure.logging.CorrelationIdFilter;
import com.aditi.authservice.infrastructure.logging.RequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the cross-cutting servlet filters with the container.
 *
 * <h2>Why {@code FilterRegistrationBean} and not {@code @Component}</h2>
 * Spring Boot automatically wraps any {@code Filter} <em>bean</em> in a
 * registration. That is convenient, but it also means the only control you have
 * over <em>when</em> a filter runs is a magic ordering constant. Declaring a
 * {@link FilterRegistrationBean} makes every relevant decision explicit and
 * inspectable: URL pattern, dispatcher types, order, and — the one that matters
 * in production — whether it is enabled at all.
 *
 * <h2>Order matters, and it is a real ordering</h2>
 * The correlation filter must run first, before anything that might log, so
 * that every later line has an id. The request logger wraps the rest, so its
 * measured duration includes the whole downstream chain. Hence
 * {@code HIGHEST_PRECEDENCE} and {@code HIGHEST_PRECEDENCE + 1} — the same
 * ordering is repeated in the {@code @Order} annotations, so the sequence is
 * visible whichever file you open.
 */
@Configuration
public class RequestFilterConfig {

    private static final String[] ALL_URLS = {"/*"};

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());

        registration.addUrlPatterns(ALL_URLS);
        registration.setName("correlationIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        // ERROR dispatches too, so a failure handled by the container still
        // carries a correlation id.
        registration.setDispatcherTypes(
                jakarta.servlet.DispatcherType.REQUEST,
                jakarta.servlet.DispatcherType.ASYNC,
                jakarta.servlet.DispatcherType.ERROR);

        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());

        registration.addUrlPatterns(ALL_URLS);
        registration.setName("requestLoggingFilter");
        // Immediately after the correlation id is established, so this line — and
        // everything it triggers — is already labelled.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setDispatcherTypes(
                jakarta.servlet.DispatcherType.REQUEST,
                jakarta.servlet.DispatcherType.ERROR);

        return registration;
    }
}
