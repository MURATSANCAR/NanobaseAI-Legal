package com.nanobase.specai.analysis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.api.AnalysisContracts.ExtractionJobResponse;
import com.nanobase.specai.analysis.application.RequirementExtractionJobService;
import com.nanobase.specai.analysis.domain.RequirementExtractionJob;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class RequirementExtractionController {
    private final RequirementExtractionJobService jobs;
    private final ExtractionEventStream eventStream;

    public RequirementExtractionController(RequirementExtractionJobService jobs,
                                           ExtractionEventStream eventStream) {
        this.jobs = jobs;
        this.eventStream = eventStream;
    }

    @PostMapping("/documents/{documentId}/requirement-extractions")
    ResponseEntity<ExtractionJobResponse> create(@PathVariable UUID documentId) {
        RequirementExtractionJob job = jobs.create(documentId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ExtractionJobResponse.from(job));
    }

    @GetMapping("/requirement-extractions/{jobId}")
    ExtractionJobResponse get(@PathVariable UUID jobId) {
        return ExtractionJobResponse.from(jobs.get(jobId));
    }

    @GetMapping(value = "/requirement-extractions/{jobId}/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID jobId) throws IOException {
        RequirementExtractionJob job = jobs.get(jobId);
        List<Map<String, Object>> existing = jobs.events(jobId);
        SseEmitter emitter = eventStream.subscribe(jobId);
        for (Map<String, Object> event : existing) {
            emitter.send(SseEmitter.event()
                .id(String.valueOf(event.get("id")))
                .name(String.valueOf(event.get("eventType")))
                .data(event));
        }
        if (Set.of("COMPLETED", "FAILED", "CANCELLED").contains(job.status())) {
            emitter.complete();
        }
        return emitter;
    }

    @PostMapping("/requirement-extractions/{jobId}/cancel")
    ExtractionJobResponse cancel(@PathVariable UUID jobId) {
        return ExtractionJobResponse.from(jobs.cancel(jobId));
    }

    @PostMapping("/requirement-extractions/{jobId}/reprocess")
    ResponseEntity<ExtractionJobResponse> reprocess(@PathVariable UUID jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ExtractionJobResponse.from(jobs.reprocess(jobId)));
    }
}
