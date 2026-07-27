package com.nanobase.specai.document.api;

import com.nanobase.specai.document.application.DocumentProcessingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/documents")
public class InternalDocumentController {
    private final DocumentProcessingService service;
    private final String workerToken;

    public InternalDocumentController(DocumentProcessingService service,
                                      @Value("${specai.worker.token}") String workerToken) {
        this.service = service;
        this.workerToken = workerToken;
    }

    @PostMapping("/processing-result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void processingResult(@RequestHeader("X-Worker-Token") String token,
                          @Valid @RequestBody ProcessingContracts.ProcessingResult result) {
        if (!constantTimeEquals(workerToken, token)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid worker credential");
        }
        service.accept(result);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
            expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
