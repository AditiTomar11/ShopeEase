package com.aditi.order_service.infrastructure.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign client tuning.
 *
 * <h2>Every number here is a trade-off between latency and false failure</h2>
 * On a free-tier backend a cold start takes 1–3 minutes, so a short timeout
 * reports failures that would have succeeded. On a paid backend with a warm
 * instance, a generous timeout means a hung request holds a worker thread for
 * minutes. These values assume the worst case (a sleeping free instance) and
 * should be lowered once a warm-tier deployment or a connection pool is in
 * place.
 *
 * <h2>Why retries are OFF by default</h2>
 * A naive retry is dangerous without an idempotency guarantee: retry a
 * {@code POST /orders} after a timeout and you may create the order twice.
 * Retries are only safe for idempotent operations (GET, PUT with a fixed id)
 * or with an idempotency key. Rather than enable that footgun globally, the
 * adapter treats a 5xx as a genuine outage and reports it as
 * {@code ProductUnavailableException}.
 */
@Configuration
public class FeignConfig {

    @Bean
    public Request.Options requestOptions() {
        // connectTimeout is time to establish the TCP+TLS connection; readTimeout
        // is time waiting for the response once connected. They fail differently:
        // a bad URL fails the first, a slow/sleeping service fails the second.
        return new Request.Options(
                10, TimeUnit.SECONDS,   // connect
                90, TimeUnit.SECONDS);  // read — generous, because the target may be waking up
    }

    @Bean
    public Retryer retryer() {
        // No automatic retries. See the class comment.
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        // BASIC logs method, URL, status and duration. FULL would log headers —
        // including the Authorization header we forward — and request/response
        // bodies, which here would contain a password hash. Never FULL in prod.
        return Logger.Level.BASIC;
    }

    /**
     * Feign's default error decoder throws a generic FeignException for every
     * non-2xx, which loses the status code. This one preserves it, so the
     * adapter can distinguish 404 ("no such product") from 503 ("the product
     * service is down") — the difference between an empty result and an outage.
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return ErrorDecoder.defaultDecoder();
    }
}
