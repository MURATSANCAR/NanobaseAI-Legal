package com.nanobase.specai.document.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component("documentIntelligence")
public class DocumentIntelligenceHealthIndicator implements HealthIndicator {
    private final boolean enabled;
    private final RestClient client;

    public DocumentIntelligenceHealthIndicator(
        RestClient.Builder builder,
        @Value("${specai.document-intelligence.enabled:false}") boolean enabled,
        @Value("${specai.document-intelligence.docling.base-url:http://localhost:8090}")
        String baseUrl) {
        this.enabled = enabled;
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Health health() {
        if (!enabled) {
            return Health.up().withDetail("state", "disabled").build();
        }
        try {
            client.get().uri("/health/ready").retrieve().toBodilessEntity();
            return Health.up().withDetail("state", "docling-ready")
                .withDetail("openContracts", "optional").build();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("state", "docling-unavailable")
                .withDetail("openContracts", "optional").build();
        }
    }
}
