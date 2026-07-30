package com.nanobase.specai.compliance.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.compliance.application.ComplianceFaultInjection;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local/staging-only control plane for compliance fault injection.
 * Requires matching token; returns 404 when injection is disabled.
 */
@RestController
@RequestMapping("/api/v1/internal/compliance-fault-injection")
public class ComplianceFaultInjectionController {
    private final ComplianceFaultInjection faultInjection;
    private final String token;

    public ComplianceFaultInjectionController(
        ComplianceFaultInjection faultInjection,
        @Value("${specai.compliance.fault-injection.token:}") String token
    ) {
        this.faultInjection = faultInjection;
        this.token = token == null ? "" : token.trim();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> status(
        @RequestHeader(value = "X-Fault-Injection-Token", required = false) String provided
    ) {
        if (!authorized(provided)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(faultInjection.snapshot());
    }

    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> replaceRules(
        @RequestHeader(value = "X-Fault-Injection-Token", required = false) String provided,
        @RequestBody JsonNode body
    ) {
        if (!authorized(provided) || !faultInjection.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        faultInjection.replaceRules(body);
        return ResponseEntity.ok(faultInjection.snapshot());
    }

    @PostMapping("/release")
    public ResponseEntity<Map<String, Object>> release(
        @RequestHeader(value = "X-Fault-Injection-Token", required = false) String provided,
        @RequestParam String matchKey,
        @RequestParam String action
    ) {
        if (!authorized(provided) || !faultInjection.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        faultInjection.releasePause(matchKey, action);
        return ResponseEntity.ok(Map.of("released", true, "matchKey", matchKey, "action", action));
    }

    private boolean authorized(String provided) {
        if (!faultInjection.enabled() || token.isBlank()) {
            return false;
        }
        return token.equals(provided == null ? "" : provided.trim());
    }
}
