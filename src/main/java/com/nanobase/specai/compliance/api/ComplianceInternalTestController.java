package com.nanobase.specai.compliance.api;

import com.nanobase.specai.compliance.application.ComplianceFaultInjection;
import com.nanobase.specai.compliance.application.ComplianceJobTransactionService;
import com.nanobase.specai.compliance.application.ComplianceJobTransactionService.JobFinalizationResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/compliance-jobs")
public class ComplianceInternalTestController {
    private final ComplianceJobTransactionService transactions;
    private final ComplianceFaultInjection faultInjection;
    private final String token;

    public ComplianceInternalTestController(
        ComplianceJobTransactionService transactions,
        ComplianceFaultInjection faultInjection,
        @Value("${specai.compliance.fault-injection.token:}") String token
    ) {
        this.transactions = transactions;
        this.faultInjection = faultInjection;
        this.token = token == null ? "" : token.trim();
    }

    @PostMapping("/{organizationId}/{jobId}/finalize")
    public ResponseEntity<Map<String, Object>> finalizeJob(
        @RequestHeader(value = "X-Fault-Injection-Token", required = false) String provided,
        @PathVariable UUID organizationId,
        @PathVariable UUID jobId
    ) {
        if (!authorized(provided)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        JobFinalizationResult result = transactions.finalizeJob(organizationId, jobId);
        return ResponseEntity.ok(Map.of(
            "status", result.status(),
            "completed", result.completed(),
            "failed", result.failed(),
            "processed", result.processed()
        ));
    }

    private boolean authorized(String provided) {
        if (!faultInjection.enabled() || token.isBlank()) {
            return false;
        }
        return token.equals(provided == null ? "" : provided.trim());
    }
}
