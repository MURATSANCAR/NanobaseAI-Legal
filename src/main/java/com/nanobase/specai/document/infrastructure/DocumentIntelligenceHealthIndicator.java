package com.nanobase.specai.document.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("documentIntelligence")
public class DocumentIntelligenceHealthIndicator implements HealthIndicator {
    private final boolean enabled;

    public DocumentIntelligenceHealthIndicator(
        @Value("${specai.document-intelligence.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Health health() {
        return Health.up()
            .withDetail("state", enabled ? "enabled" : "disabled")
            .build();
    }
}
