package com.nanobase.specai.knowledge.api;

import com.nanobase.specai.analysis.api.ExtractionEventStream;
import com.nanobase.specai.knowledge.application.KnowledgeExtractionJobService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class KnowledgeExtractionController {
    private final KnowledgeExtractionJobService jobs;
    private final ExtractionEventStream streams;

    public KnowledgeExtractionController(KnowledgeExtractionJobService jobs,
                                         ExtractionEventStream streams) {
        this.jobs = jobs;
        this.streams = streams;
    }

    @PostMapping("/documents/{documentId}/knowledge-extractions")
    ResponseEntity<Map<String, Object>> create(
        @PathVariable UUID documentId,
        @RequestBody(required = false) CreateKnowledgeExtractionRequest request
    ) {
        UUID role = request == null ? null : request.documentRoleConceptId();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(jobs.create(documentId, role));
    }

    @GetMapping("/knowledge-extractions/{jobId}")
    Map<String, Object> get(@PathVariable UUID jobId) {
        return jobs.get(jobId);
    }

    @GetMapping(value = "/knowledge-extractions/{jobId}/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID jobId) throws IOException {
        Map<String, Object> job = jobs.get(jobId);
        List<Map<String, Object>> existing = jobs.events(jobId);
        SseEmitter emitter = streams.subscribe(jobId);
        for (Map<String, Object> event : existing) {
            emitter.send(SseEmitter.event()
                .id(String.valueOf(event.get("id")))
                .name(String.valueOf(event.get("eventType")))
                .data(event));
        }
        if (Set.of("COMPLETED", "FAILED", "CANCELLED")
            .contains(String.valueOf(job.get("status")))) {
            emitter.complete();
        }
        return emitter;
    }

    @PostMapping("/knowledge-extractions/{jobId}/cancel")
    Map<String, Object> cancel(@PathVariable UUID jobId) {
        return jobs.cancel(jobId);
    }

    public record CreateKnowledgeExtractionRequest(UUID documentRoleConceptId) {
    }
}
