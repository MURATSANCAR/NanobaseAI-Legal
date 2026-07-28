package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.workflow.api.ReportingContracts.CreateReportDefinitionRequest;
import com.nanobase.specai.workflow.api.ReportingContracts.CreateReportVersionRequest;
import com.nanobase.specai.workflow.api.ReportingContracts.DownloadUrlResponse;
import com.nanobase.specai.workflow.api.ReportingContracts.GenerateReportRequest;
import com.nanobase.specai.workflow.api.ReportingContracts.PreviewReportRequest;
import com.nanobase.specai.workflow.api.ReportingContracts.ReportArtifactResponse;
import com.nanobase.specai.workflow.api.ReportingContracts.ReportDefinitionResponse;
import com.nanobase.specai.workflow.api.ReportingContracts.ReportJobResponse;
import com.nanobase.specai.workflow.application.DynamicReportingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportingController {
    private final DynamicReportingService reports;

    public ReportingController(DynamicReportingService reports) {
        this.reports = reports;
    }

    @PostMapping("/report-definitions")
    ReportDefinitionResponse create(
        @Valid @RequestBody CreateReportDefinitionRequest request) {
        return reports.createDefinition(request);
    }

    @GetMapping("/report-definitions")
    List<ReportDefinitionResponse> list() {
        return reports.definitions();
    }

    @PostMapping("/report-definitions/{id}/versions")
    Map<String, Object> version(@PathVariable UUID id,
                                @Valid @RequestBody
                                CreateReportVersionRequest request) {
        return reports.createVersion(id, request);
    }

    @PostMapping("/report-definition-versions/{id}/activate")
    Map<String, Object> activate(@PathVariable UUID id) {
        return reports.activateVersion(id);
    }

    @PostMapping("/report-definition-versions/{id}/preview")
    ObjectNode preview(@PathVariable UUID id,
                       @Valid @RequestBody PreviewReportRequest request) {
        return reports.preview(id, request.projectId());
    }

    @PostMapping("/tenders/{projectId}/reports")
    ReportJobResponse generate(@PathVariable UUID projectId,
                               @Valid @RequestBody GenerateReportRequest request) {
        return reports.generate(projectId, request);
    }

    @GetMapping("/reports/{jobId}")
    ReportJobResponse get(@PathVariable UUID jobId) {
        return reports.job(jobId);
    }

    @GetMapping("/reports/{jobId}/artifacts")
    List<ReportArtifactResponse> artifacts(@PathVariable UUID jobId) {
        return reports.artifacts(jobId);
    }

    @GetMapping("/report-artifacts/{id}/download-url")
    DownloadUrlResponse download(@PathVariable UUID id) {
        return reports.download(id);
    }
}
