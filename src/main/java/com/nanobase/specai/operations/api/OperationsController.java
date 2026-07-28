package com.nanobase.specai.operations.api;

import com.nanobase.specai.operations.application.OperationsReadinessService;
import com.nanobase.specai.operations.application.OperationsReadinessService.AiQualitySnapshot;
import com.nanobase.specai.operations.application.OperationsReadinessService.Snapshot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TENANT_ADMIN')")
public class OperationsController {
    private final OperationsReadinessService readiness;

    public OperationsController(OperationsReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/readiness")
    public Snapshot readiness() {
        return readiness.snapshot();
    }

    @GetMapping("/ai-quality")
    public AiQualitySnapshot aiQuality() {
        return readiness.aiQuality();
    }
}
