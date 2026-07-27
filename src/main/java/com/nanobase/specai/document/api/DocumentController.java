package com.nanobase.specai.document.api;

import com.nanobase.specai.document.api.DocumentContracts.ClauseResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentVersionResponse;
import com.nanobase.specai.document.api.DocumentContracts.DownloadUrlResponse;
import com.nanobase.specai.document.application.DocumentService;
import com.nanobase.specai.document.application.ProcessingEventPublisher;
import com.nanobase.specai.document.domain.DocumentType;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {
    private final DocumentService service;
    private final ProcessingEventPublisher processingEvents;

    public DocumentController(DocumentService service, ProcessingEventPublisher processingEvents) {
        this.service = service;
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

    @GetMapping("/documents/{documentId}/clauses")
    List<ClauseResponse> clauses(@PathVariable UUID documentId) {
        return service.clauses(documentId);
    }

    @GetMapping(value = "/documents/{documentId}/processing-events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter processingEvents(@PathVariable UUID documentId) {
        DocumentResponse document = service.get(documentId);
        return processingEvents.subscribe(documentId, document.currentVersionId(), document.status());
    }
}
