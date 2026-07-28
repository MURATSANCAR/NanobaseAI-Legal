package com.nanobase.specai.operations.application;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionConfigurationGuard {
    private static final List<String> TLS_ENDPOINTS = List.of(
        "spring.datasource.url",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        "specai.storage.endpoint",
        "specai.ai-orchestrator.base-url",
        "specai.document-intelligence.docling.base-url"
    );
    private final Environment environment;

    public ProductionConfigurationGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (environment.getProperty("specai.bootstrap.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Local tenant bootstrap is forbidden in production");
        }
        if (!environment.getProperty("specai.file-security.clamav.enabled",
            Boolean.class, true)) {
            throw new IllegalStateException("Malware scanning is mandatory in production");
        }
        for (String property : TLS_ENDPOINTS) {
            String value = environment.getProperty(property);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(property + " is required in production");
            }
            requireTls(property, value);
        }
    }

    private void requireTls(String property, String value) {
        if (value.startsWith("jdbc:postgresql:")) {
            if (!value.contains("sslmode=verify-full")) {
                throw new IllegalStateException(
                    "PostgreSQL must use sslmode=verify-full in production");
            }
            return;
        }
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException(property + " must use HTTPS in production");
        }
    }
}
