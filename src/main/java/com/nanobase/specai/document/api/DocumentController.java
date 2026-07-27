package com.nanobase.specai.document.api;

import com.nanobase.specai.document.api.DocumentContracts.DocumentPreviewResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentResponse;
import com.nanobase.specai.document.application.DocumentService;
import com.nanobase.specai.document.domain.DocumentType;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(value = "/tenders/{projectId}/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentResponse upload(@PathVariable UUID projectId,
                            @RequestParam DocumentType type,
                            @RequestParam MultipartFile file) {
        return service.upload(projectId, type, file);
    }

    @GetMapping("/tenders/{projectId}/documents")
    List<DocumentResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @GetMapping("/documents/{documentId}/preview")
    DocumentPreviewResponse preview(@PathVariable UUID documentId) {
        URI url = service.preview(documentId);
        return new DocumentPreviewResponse(url.toString(), 600);
    }
}
