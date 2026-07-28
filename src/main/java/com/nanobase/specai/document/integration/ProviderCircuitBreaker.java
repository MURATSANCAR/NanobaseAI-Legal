package com.nanobase.specai.document.integration;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProviderCircuitBreaker {
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    @Autowired
    public ProviderCircuitBreaker(
        @Value("${specai.document-intelligence.circuit-breaker.failure-threshold:5}")
        int failureThreshold,
        @Value("${specai.document-intelligence.circuit-breaker.open-duration:PT30S}")
        Duration openDuration) {
        this(failureThreshold, openDuration, Clock.systemUTC());
    }

    ProviderCircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public void requireAvailable(String provider) {
        State state = states.get(provider);
        if (state != null && state.openedAt != null
            && clock.instant().isBefore(state.openedAt.plus(openDuration))) {
            throw new ProviderUnavailableException(provider + " circuit breaker is open");
        }
        if (state != null && state.openedAt != null) {
            states.remove(provider, state);
        }
    }

    public void success(String provider) {
        states.remove(provider);
    }

    public void failure(String provider) {
        states.compute(provider, (ignored, current) -> {
            int failures = current == null ? 1 : current.failures + 1;
            return new State(failures,
                failures >= failureThreshold ? clock.instant() : null);
        });
    }

    private record State(int failures, Instant openedAt) {
    }
}
