package com.nanobase.specai.pilot.api;

import com.nanobase.specai.pilot.api.PilotContracts.AssignFeedbackRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CanaryRequest;
import com.nanobase.specai.pilot.api.PilotContracts.AdjudicationRequest;
import com.nanobase.specai.pilot.api.PilotContracts.ComparisonResultRequest;
import com.nanobase.specai.pilot.api.PilotContracts.ConfigurationRollbackRequest;
import com.nanobase.specai.pilot.api.PilotContracts.ConceptResponse;
import com.nanobase.specai.pilot.api.PilotContracts.CreateConfigurationSnapshotRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateDisagreementRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateExperimentRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateFeedbackRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateImprovementCandidateRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreatePilotSessionRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateQualityDebtRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateRegressionSuiteRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateReproductionPackageRequest;
import com.nanobase.specai.pilot.api.PilotContracts.PilotDashboardResponse;
import com.nanobase.specai.pilot.api.PilotContracts.RecordExperimentResultRequest;
import com.nanobase.specai.pilot.api.PilotContracts.RecordPilotEventRequest;
import com.nanobase.specai.pilot.api.PilotContracts.RecordPilotMetricRequest;
import com.nanobase.specai.pilot.api.PilotContracts.ResolveFeedbackRequest;
import com.nanobase.specai.pilot.api.PilotContracts.StartExperimentRunRequest;
import com.nanobase.specai.pilot.api.PilotContracts.TriageFeedbackRequest;
import com.nanobase.specai.pilot.application.PilotLifecycleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PilotController {
    private final PilotLifecycleService service;

    public PilotController(PilotLifecycleService service) {
        this.service = service;
    }

    @GetMapping("/pilot-concepts/{catalogCode}")
    List<ConceptResponse> concepts(@PathVariable String catalogCode) {
        return service.concepts(catalogCode);
    }

    @PostMapping("/configuration-snapshots")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> createSnapshot(
        @Valid @RequestBody CreateConfigurationSnapshotRequest request
    ) {
        return service.createConfigurationSnapshot(request);
    }

    @GetMapping("/configuration-snapshots/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TECHNICAL_REVIEWER')")
    Map<String, Object> snapshot(@PathVariable UUID id) {
        return service.snapshot(id);
    }

    @PostMapping("/pilot-sessions")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TENDER_MANAGER')")
    Map<String, Object> createPilotSession(
        @Valid @RequestBody CreatePilotSessionRequest request
    ) {
        return service.createPilotSession(request);
    }

    @GetMapping("/pilot-sessions/{id}")
    Map<String, Object> pilotSession(@PathVariable UUID id) {
        return service.pilotSession(id);
    }

    @PostMapping("/pilot-sessions/{id}/events")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TENDER_MANAGER','TECHNICAL_REVIEWER')")
    Map<String, Object> pilotEvent(
        @PathVariable UUID id,
        @Valid @RequestBody RecordPilotEventRequest request
    ) {
        return service.recordPilotEvent(id, request);
    }

    @PostMapping("/pilot-sessions/{id}/metrics")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> pilotMetric(
        @PathVariable UUID id,
        @Valid @RequestBody RecordPilotMetricRequest request
    ) {
        return service.recordPilotMetric(id, request);
    }

    @PostMapping("/feedback")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TENDER_MANAGER','TECHNICAL_REVIEWER')")
    Map<String, Object> createFeedback(
        @Valid @RequestBody CreateFeedbackRequest request
    ) {
        return service.createFeedback(request);
    }

    @GetMapping("/feedback")
    List<Map<String, Object>> feedback(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) UUID projectId
    ) {
        return service.feedbackList(status, severity, projectId);
    }

    @GetMapping("/feedback/{id}")
    Map<String, Object> feedback(@PathVariable UUID id) {
        return service.feedback(id);
    }

    @PostMapping("/feedback/{id}/triage")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TENDER_MANAGER','TECHNICAL_REVIEWER')")
    Map<String, Object> triage(
        @PathVariable UUID id,
        @Valid @RequestBody TriageFeedbackRequest request
    ) {
        return service.triageFeedback(id, request);
    }

    @PostMapping("/feedback/{id}/assign")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TENDER_MANAGER')")
    Map<String, Object> assign(
        @PathVariable UUID id,
        @Valid @RequestBody AssignFeedbackRequest request
    ) {
        return service.assignFeedback(id, request);
    }

    @PostMapping("/feedback/{id}/resolve")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TENDER_MANAGER')")
    Map<String, Object> resolve(
        @PathVariable UUID id,
        @Valid @RequestBody ResolveFeedbackRequest request
    ) {
        return service.resolveFeedback(id, request);
    }

    @GetMapping("/feedback/{id}/history")
    List<Map<String, Object>> history(@PathVariable UUID id) {
        return service.feedbackHistory(id);
    }

    @PostMapping("/reproduction-packages")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TECHNICAL_REVIEWER')")
    Map<String, Object> reproduction(
        @Valid @RequestBody CreateReproductionPackageRequest request
    ) {
        return service.createReproductionPackage(request);
    }

    @PostMapping("/regression-suites")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> regressionSuite(
        @Valid @RequestBody CreateRegressionSuiteRequest request
    ) {
        return service.createRegressionSuite(request);
    }

    @PostMapping("/improvement-candidates")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> createCandidate(
        @Valid @RequestBody CreateImprovementCandidateRequest request
    ) {
        return service.createImprovementCandidate(request);
    }

    @GetMapping("/improvement-candidates")
    List<Map<String, Object>> candidates() {
        return service.candidates();
    }

    @GetMapping("/improvement-candidates/{id}")
    Map<String, Object> candidate(@PathVariable UUID id) {
        return service.candidate(id);
    }

    @PostMapping("/improvement-candidates/{id}/experiments")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> createExperiment(
        @PathVariable UUID id,
        @Valid @RequestBody CreateExperimentRequest request
    ) {
        return service.createExperiment(id, request);
    }

    @PostMapping("/improvement-candidates/{id}/shadow")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> shadow(@PathVariable UUID id) {
        return service.startShadow(id);
    }

    @PostMapping("/improvement-candidates/{id}/canary")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> canary(
        @PathVariable UUID id,
        @Valid @RequestBody CanaryRequest request
    ) {
        return service.startCanary(id, request);
    }

    @PostMapping("/improvement-candidates/{id}/activate")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> activate(@PathVariable UUID id) {
        return service.activateCandidate(id);
    }

    @PostMapping("/improvement-candidates/{id}/reject")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> reject(@PathVariable UUID id) {
        return service.rejectCandidate(id);
    }

    @GetMapping("/experiments")
    List<Map<String, Object>> experiments() {
        return service.experiments();
    }

    @GetMapping("/experiments/{id}")
    Map<String, Object> experiment(@PathVariable UUID id) {
        return service.experiment(id);
    }

    @PostMapping("/experiments/{id}/runs")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> run(
        @PathVariable UUID id,
        @RequestBody(required = false) StartExperimentRunRequest request
    ) {
        return service.startExperimentRun(id, request);
    }

    @GetMapping("/experiment-runs/{id}")
    Map<String, Object> experimentRun(@PathVariable UUID id) {
        return service.experimentRun(id);
    }

    @GetMapping("/experiment-runs/{id}/results")
    List<Map<String, Object>> experimentResults(@PathVariable UUID id) {
        return service.experimentResults(id);
    }

    @PostMapping("/experiment-runs/{id}/results")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> recordExperimentResult(
        @PathVariable UUID id,
        @Valid @RequestBody RecordExperimentResultRequest request
    ) {
        return service.recordExperimentResult(id, request);
    }

    @PostMapping("/shadow-executions/{id}/results")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> shadowResult(
        @PathVariable UUID id,
        @Valid @RequestBody ComparisonResultRequest request
    ) {
        return service.completeShadow(id, request.comparison(), request.passed());
    }

    @PostMapping("/canary-assignments/{id}/results")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> canaryResult(
        @PathVariable UUID id,
        @Valid @RequestBody ComparisonResultRequest request
    ) {
        return service.completeCanary(id, request.comparison(), request.passed());
    }

    @PostMapping("/configuration-activations/rollback")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> configurationRollback(
        @Valid @RequestBody ConfigurationRollbackRequest request
    ) {
        return service.rollbackConfiguration(request);
    }

    @PostMapping("/quality-debt")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> qualityDebt(
        @Valid @RequestBody CreateQualityDebtRequest request
    ) {
        return service.createQualityDebt(request);
    }

    @PostMapping("/quality-debt/{id}/accept")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN')")
    Map<String, Object> acceptQualityDebt(@PathVariable UUID id) {
        return service.acceptQualityDebt(id);
    }

    @PostMapping("/review-disagreements")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TECHNICAL_REVIEWER')")
    Map<String, Object> disagreement(
        @Valid @RequestBody CreateDisagreementRequest request
    ) {
        return service.createDisagreement(request);
    }

    @PostMapping("/review-disagreements/{id}/adjudicate")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','TECHNICAL_REVIEWER')")
    Map<String, Object> adjudicate(
        @PathVariable UUID id,
        @Valid @RequestBody AdjudicationRequest request
    ) {
        return service.adjudicate(id, request);
    }

    @GetMapping("/pilot-quality-dashboard")
    PilotDashboardResponse dashboard() {
        return service.dashboard();
    }
}
