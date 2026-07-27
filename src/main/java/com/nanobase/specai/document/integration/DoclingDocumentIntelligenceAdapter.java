package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.domain.DocumentStatus;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Qualifier("doclingAdapter")
public class DoclingDocumentIntelligenceAdapter implements DocumentIntelligencePort {
    private static final String PROVIDER = "DOCLING";
    private final RestClient client;
    private final ProviderCircuitBreaker circuitBreaker;
    private final String bucket;

    public DoclingDocumentIntelligenceAdapter(
        RestClient.Builder builder,
        ProviderCircuitBreaker circuitBreaker,
        @Value("${specai.document-intelligence.docling.base-url:http://localhost:8090}")
        String baseUrl,
        @Value("${specai.storage.original-bucket:specai-original}") String bucket) {
        this.client = builder.baseUrl(baseUrl).build();
        this.circuitBreaker = circuitBreaker;
        this.bucket = bucket;
    }

    @Override
    public ProcessingSubmission submit(DocumentProcessingCommand command) {
        ParseResponse response = execute(() -> client.post().uri("/v1/documents/parse")
            .body(new ParseRequest(command.documentVersionId(),
                new Source("S3_COMPATIBLE", bucket, command.objectStorageKey()),
                command.mimeType(), command.languageHint(),
                command.ocrRequired() ? "FORCED" : "AUTO", true, false,
                command.correlationId()))
            .retrieve().body(ParseResponse.class));
        if (response == null || response.jobId() == null) {
            throw new ProviderUnavailableException("Docling response is missing jobId");
        }
        return new ProcessingSubmission(reference(response.jobId(), command.organizationId(),
            command.documentVersionId()), status(response.status()));
    }

    @Override
    public ProcessingStatusResult getStatus(ExternalProcessingReference reference) {
        JobResponse response = execute(() -> client.get().uri("/v1/jobs/{jobId}",
            reference.externalReference()).retrieve().body(JobResponse.class));
        if (response == null) {
            throw new ProviderUnavailableException("Docling returned an empty job response");
        }
        return new ProcessingStatusResult(status(response.status()), response.currentStage(),
            response.progress(), response.message(), response.errorCode());
    }

    @Override
    public DocumentExtractionResult getResult(ExternalProcessingReference reference) {
        DocumentExtractionResult result = execute(() -> client.get()
            .uri("/v1/jobs/{jobId}/result", reference.externalReference())
            .retrieve().body(DocumentExtractionResult.class));
        if (result == null || !reference.documentVersionId().equals(result.documentVersionId())) {
            throw new ProviderUnavailableException("Docling returned an invalid result schema");
        }
        return result;
    }

    @Override
    public void cancel(ExternalProcessingReference reference) {
        execute(() -> {
            client.post().uri("/v1/jobs/{jobId}/cancel", reference.externalReference())
                .retrieve().toBodilessEntity();
            return Boolean.TRUE;
        });
    }

    private ExternalProcessingReference reference(
        UUID jobId, UUID organizationId, UUID documentVersionId) {
        return new ExternalProcessingReference(PROVIDER, jobId.toString(),
            organizationId, documentVersionId);
    }

    private DocumentStatus status(String value) {
        if (value == null) {
            return DocumentStatus.QUEUED;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "PENDING", "QUEUED" -> DocumentStatus.QUEUED;
            case "RUNNING", "PARSING" -> DocumentStatus.PARSING;
            case "OCR_PROCESSING" -> DocumentStatus.OCR_PROCESSING;
            case "STRUCTURE_DETECTION" -> DocumentStatus.STRUCTURE_DETECTION;
            case "INDEXING" -> DocumentStatus.INDEXING;
            case "COMPLETED", "READY" -> DocumentStatus.READY;
            case "MANUAL_REVIEW_REQUIRED" -> DocumentStatus.MANUAL_REVIEW_REQUIRED;
            case "CANCELLED" -> DocumentStatus.CANCELLED;
            case "FAILED" -> DocumentStatus.FAILED;
            default -> throw new ProviderUnavailableException(
                "Docling returned an unknown status");
        };
    }

    private <T> T execute(ProviderCall<T> call) {
        circuitBreaker.requireAvailable(PROVIDER);
        try {
            T value = call.execute();
            circuitBreaker.success(PROVIDER);
            return value;
        } catch (ProviderUnavailableException exception) {
            circuitBreaker.failure(PROVIDER);
            throw exception;
        } catch (RuntimeException exception) {
            circuitBreaker.failure(PROVIDER);
            throw new ProviderUnavailableException("Docling request failed", exception);
        }
    }

    private interface ProviderCall<T> {
        T execute();
    }

    private record Source(String type, String bucket, String objectKey) {
    }

    private record ParseRequest(
        UUID documentVersionId,
        Source source,
        String mimeType,
        String languageHint,
        String ocrMode,
        boolean extractTables,
        boolean extractImages,
        UUID correlationId
    ) {
    }

    private record ParseResponse(UUID jobId, String status) {
    }

    private record JobResponse(UUID jobId, String status, String currentStage,
                               int progress, String message, String errorCode) {
    }
}
