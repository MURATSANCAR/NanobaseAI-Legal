package com.nanobase.specai.document.api;

import com.nanobase.specai.document.api.DocumentContracts.ClauseResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentVersionResponse;
import com.nanobase.specai.document.api.DocumentContracts.DownloadUrlResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentPageResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentTableResponse;
import com.nanobase.specai.document.api.DocumentContracts.PageResponse;
import com.nanobase.specai.document.api.DocumentContracts.ProcessingJobResponse;
import com.nanobase.specai.document.application.DocumentQueryService;
import com.nanobase.specai.document.application.DocumentService;
import com.nanobase.specai.document.application.ProcessingEventStreamService;
import com.nanobase.specai.document.domain.DocumentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1")
@Validated
public class DocumentController {
    private final DocumentService service;
    private final DocumentQueryService queries;
    private final ProcessingEventStreamService processingEvents;

    public DocumentController(DocumentService service, DocumentQueryService queries,
                              ProcessingEventStreamService processingEvents) {
        this.service = service;
        this.queries = queries;
        this.processingEvents = processingEvents;
    }

    @PostMapping(value = "/tenders/{projectId}/documents",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    DocumentResponse upload(@PathVariable UUID projectId,
                            @RequestParam DocumentType documentType,
                            @RequestParam(required = false) String logicalName,
                            @RequestParam(defaultValue = "true") boolean includedInAnalysis,
                            @RequestParam MultipartFile file) {
        return service.upload(projectId, documentType, logicalName, includedInAnalysis, file);
    }

    @GetMapping("/tenders/{projectId}/documents")
    List<DocumentResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @GetMapping("/documents/{documentId}")
    DocumentResponse get(@PathVariable UUID documentId) {
        return service.get(documentId);
    }

    @GetMapping("/documents/{documentId}/versions")
    List<DocumentVersionResponse> versions(@PathVariable UUID documentId) {
        return service.versions(documentId);
    }

    @PostMapping(value = "/documents/{documentId}/versions",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    DocumentResponse uploadVersion(@PathVariable UUID documentId,
                                   @RequestParam MultipartFile file) {
        return service.uploadVersion(documentId, file);
    }

    @PostMapping("/documents/{documentId}/reprocess")
    DocumentResponse reprocess(@PathVariable UUID documentId) {
        return service.reprocess(documentId);
    }

    @GetMapping("/documents/{documentId}/download-url")
    DownloadUrlResponse downloadUrl(@PathVariable UUID documentId) {
        URI url = service.downloadUrl(documentId);
        return new DownloadUrlResponse(url.toString(), 300);
    }

    @GetMapping("/documents/{documentId}/download")
    org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource>
    download(@PathVariable UUID documentId) {
        DocumentService.StreamingDownload stream = service.openDownload(documentId);
        return org.springframework.http.ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + stream.fileName() + "\"")
            .contentType(MediaType.parseMediaType(stream.mimeType()))
            .contentLength(stream.size())
            .body(new org.springframework.core.io.InputStreamResource(stream.content()));
    }

    @GetMapping("/documents/{documentId}/pages")
    PageResponse<DocumentPageResponse> pages(
        @PathVariable UUID documentId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return queries.pages(documentId, page, size);
    }

    @GetMapping("/documents/{documentId}/pages/{pageNumber}")
    DocumentPageResponse page(@PathVariable UUID documentId,
                              @PathVariable @Min(1) int pageNumber) {
        return queries.page(documentId, pageNumber);
    }

    @GetMapping("/documents/{documentId}/clauses")
    PageResponse<ClauseResponse> clauses(
        @PathVariable UUID documentId,
        @RequestParam(required = false) UUID parentClauseId,
        @RequestParam(required = false) String clauseType,
        @RequestParam(required = false) @Min(1) Integer pageNumber,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        return queries.clauses(documentId, parentClauseId, clauseType, pageNumber,
            search, page, size);
    }

    @GetMapping("/documents/{documentId}/clauses/{clauseId}")
    ClauseResponse clause(@PathVariable UUID documentId, @PathVariable UUID clauseId) {
        return queries.clause(documentId, clauseId);
    }

    @GetMapping("/documents/{documentId}/tables")
    PageResponse<DocumentTableResponse> tables(
        @PathVariable UUID documentId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return queries.tables(documentId, page, size);
    }

    @GetMapping("/documents/{documentId}/processing-jobs")
    PageResponse<ProcessingJobResponse> jobs(
        @PathVariable UUID documentId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.jobs(documentId, page, size);
    }

    @PostMapping("/processing-jobs/{jobId}/cancel")
    ProcessingJobResponse cancel(@PathVariable UUID jobId) {
        return queries.cancel(jobId);
    }

    @GetMapping(value = "/documents/{documentId}/processing-events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter processingEvents(
        @PathVariable UUID documentId,
        @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        return processingEvents.document(documentId, lastEventId);
    }

    @GetMapping(value = "/processing-jobs/{jobId}/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter processingJobEvents(
        @PathVariable UUID jobId,
        @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        return processingEvents.job(jobId, lastEventId);
    }
}
