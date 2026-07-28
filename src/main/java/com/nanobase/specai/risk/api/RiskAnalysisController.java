package com.nanobase.specai.risk.api;

import com.nanobase.specai.risk.application.RiskAnalysisJobService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RiskAnalysisController {
    private final RiskAnalysisJobService jobs;

    public RiskAnalysisController(RiskAnalysisJobService jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/tenders/{projectId}/risk-analyses")
    Map<String, Object> create(@PathVariable UUID projectId) {
        return jobs.create(projectId);
    }

    @GetMapping("/risk-analyses/{jobId}")
    Map<String, Object> get(@PathVariable UUID jobId) {
        return jobs.get(jobId);
    }

    @GetMapping("/risk-analyses/{jobId}/events")
    List<Map<String, Object>> events(@PathVariable UUID jobId) {
        return jobs.events(jobId);
    }

    @PostMapping("/risk-analyses/{jobId}/cancel")
    Map<String, Object> cancel(@PathVariable UUID jobId) {
        return jobs.cancel(jobId);
    }

    @PostMapping("/risk-analyses/{jobId}/retry-failed")
    Map<String, Object> retry(@PathVariable UUID jobId) {
        return jobs.retryFailed(jobId);
    }
}
