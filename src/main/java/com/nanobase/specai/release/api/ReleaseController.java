package com.nanobase.specai.release.api;

import com.nanobase.specai.release.api.ReleaseContracts.ApprovalDecisionRequest;
import com.nanobase.specai.release.api.ReleaseContracts.ApprovalRequest;
import com.nanobase.specai.release.api.ReleaseContracts.CreateReleaseRequest;
import com.nanobase.specai.release.api.ReleaseContracts.DeploymentResultRequest;
import com.nanobase.specai.release.api.ReleaseContracts.DryRunRequest;
import com.nanobase.specai.release.api.ReleaseContracts.DryRunResultRequest;
import com.nanobase.specai.release.api.ReleaseContracts.GateResultRequest;
import com.nanobase.specai.release.api.ReleaseContracts.GoLiveDecisionRequest;
import com.nanobase.specai.release.api.ReleaseContracts.ReleaseManifestRequest;
import com.nanobase.specai.release.api.ReleaseContracts.ReleaseArtifactRequest;
import com.nanobase.specai.release.api.ReleaseContracts.ReleasePackageResponse;
import com.nanobase.specai.release.api.ReleaseContracts.StabilizationRequest;
import com.nanobase.specai.release.application.ReleaseLifecycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
public class ReleaseController {
    private final ReleaseLifecycleService releases;

    public ReleaseController(ReleaseLifecycleService releases) {
        this.releases = releases;
    }

    @PostMapping("/releases")
    Map<String, Object> create(@Valid @RequestBody CreateReleaseRequest request) {
        return releases.create(request);
    }

    @GetMapping("/releases")
    List<Map<String, Object>> list() {
        return releases.list();
    }

    @GetMapping("/releases/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        return releases.get(id);
    }

    @PostMapping("/releases/{id}/gates")
    Map<String, Object> gate(
        @PathVariable UUID id,
        @Valid @RequestBody GateResultRequest request
    ) {
        return releases.recordGate(id, request);
    }

    @PostMapping("/releases/{id}/manifest")
    Map<String, Object> manifest(
        @PathVariable UUID id,
        @Valid @RequestBody ReleaseManifestRequest request
    ) {
        return releases.createManifest(id, request);
    }

    @PostMapping("/releases/{id}/artifacts")
    Map<String, Object> artifact(
        @PathVariable UUID id,
        @Valid @RequestBody ReleaseArtifactRequest request
    ) {
        return releases.addArtifact(id, request);
    }

    @GetMapping("/releases/{id}/manifest")
    Map<String, Object> manifest(@PathVariable UUID id) {
        return releases.manifest(id);
    }

    @PostMapping("/releases/{id}/approve")
    Map<String, Object> approve(
        @PathVariable UUID id,
        @RequestBody(required = false) ApprovalRequest request
    ) {
        return releases.requestApproval(id, request);
    }

    @PostMapping("/release-approvals/{id}/decisions")
    Map<String, Object> approvalDecision(
        @PathVariable UUID id,
        @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        return releases.decideApproval(id, request);
    }

    @PostMapping("/releases/{id}/dry-run")
    Map<String, Object> dryRun(
        @PathVariable UUID id,
        @Valid @RequestBody DryRunRequest request
    ) {
        return releases.requestDryRun(id, request);
    }

    @PostMapping("/release-dry-runs/{id}/results")
    Map<String, Object> dryRunResult(
        @PathVariable UUID id,
        @Valid @RequestBody DryRunResultRequest request
    ) {
        return releases.completeDryRun(id, request);
    }

    @PostMapping("/releases/{id}/deploy")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    Map<String, Object> deploy(@PathVariable UUID id) {
        return releases.requestDeploy(id);
    }

    @PostMapping("/releases/{id}/deployment-results")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    Map<String, Object> deploymentResult(
        @PathVariable UUID id,
        @Valid @RequestBody DeploymentResultRequest request
    ) {
        return releases.recordDeploymentResult(id, request);
    }

    @PostMapping("/releases/{id}/rollback")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    Map<String, Object> rollback(@PathVariable UUID id) {
        return releases.requestRollback(id);
    }

    @PostMapping("/releases/{id}/rollback-results")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    Map<String, Object> rollbackResult(
        @PathVariable UUID id,
        @Valid @RequestBody DeploymentResultRequest request
    ) {
        return releases.recordRollbackResult(id, request);
    }

    @PostMapping("/releases/{id}/go-live-decisions")
    Map<String, Object> goLiveDecision(
        @PathVariable UUID id,
        @Valid @RequestBody GoLiveDecisionRequest request
    ) {
        return releases.decideGoLive(id, request);
    }

    @GetMapping("/releases/{id}/go-live-package")
    ReleasePackageResponse goLivePackage(@PathVariable UUID id) {
        return releases.goLivePackage(id);
    }

    @PostMapping("/releases/{id}/stabilization")
    Map<String, Object> stabilization(
        @PathVariable UUID id,
        @Valid @RequestBody StabilizationRequest request
    ) {
        return releases.createStabilization(id, request);
    }

    @GetMapping("/releases/{id}/stabilization")
    Map<String, Object> stabilization(@PathVariable UUID id) {
        return releases.stabilization(id);
    }
}
