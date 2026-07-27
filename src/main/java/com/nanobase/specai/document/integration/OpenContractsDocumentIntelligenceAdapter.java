package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.application.ExternalDocumentMappingService;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.ExternalDocumentMapping;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Qualifier("openContractsAdapter")
public class OpenContractsDocumentIntelligenceAdapter
    implements DocumentIntelligencePort {
    private static final String PROVIDER = "OPENCONTRACTS";
    private final RestClient client;
    private final ProviderCircuitBreaker circuitBreaker;
    private final ExternalDocumentMappingService mappings;

    public OpenContractsDocumentIntelligenceAdapter(
        RestClient.Builder builder,
        ProviderCircuitBreaker circuitBreaker,
        ExternalDocumentMappingService mappings,
        @Value("${specai.document-intelligence.opencontracts.base-url:http://localhost:8091}")
        String baseUrl,
        @Value("${specai.document-intelligence.opencontracts.api-token:}") String apiToken) {
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        if (!apiToken.isBlank()) {
            configured.defaultHeader("Authorization", "Bearer " + apiToken);
        }
        this.client = configured.build();
        this.circuitBreaker = circuitBreaker;
        this.mappings = mappings;
    }

    @Override
    public ProcessingSubmission submit(DocumentProcessingCommand command) {
        ExternalDocumentMapping existing = mappings.find(command.organizationId(),
            command.documentVersionId(), ExternalDocumentMapping.Provider.OPENCONTRACTS)
            .orElse(null);
        String corpusId = existing == null ? null : existing.externalCorpusId();
        if (corpusId == null) {
            CorpusResponse corpus = execute(() -> client.post().uri("/api/v1/corpora")
                .body(new CorpusRequest(command.organizationId(), command.projectId()))
                .retrieve().body(CorpusResponse.class));
            if (corpus == null || corpus.id() == null || corpus.id().isBlank()) {
                throw new ProviderUnavailableException(
                    "OpenContracts response is missing corpus ID");
            }
            corpusId = corpus.id();
        }
        final String resolvedCorpusId = corpusId;
        SubmissionResponse response = execute(() -> client.post()
            .uri("/api/v1/corpora/{corpusId}/documents", resolvedCorpusId)
            .body(new SubmissionRequest(command.documentVersionId(),
                command.objectStorageKey(), command.originalFileName(), command.mimeType(),
                command.sha256(), command.correlationId()))
            .retrieve().body(SubmissionResponse.class));
        if (response == null || response.documentId() == null
            || response.documentId().isBlank()) {
            throw new ProviderUnavailableException(
                "OpenContracts response is missing external document ID");
        }
        mappings.submitted(command.organizationId(), command.documentVersionId(),
            ExternalDocumentMapping.Provider.OPENCONTRACTS, resolvedCorpusId,
            response.documentId(), response.providerVersion());
        return new ProcessingSubmission(new ExternalProcessingReference(PROVIDER,
            response.documentId(), command.organizationId(), command.documentVersionId()),
            status(response.status()));
    }

    @Override
    public ProcessingStatusResult getStatus(ExternalProcessingReference reference) {
        StatusResponse response = execute(() -> client.get()
            .uri("/api/v1/documents/{documentId}/status", reference.externalReference())
            .retrieve().body(StatusResponse.class));
        if (response == null) {
            throw new ProviderUnavailableException(
                "OpenContracts returned an empty status response");
        }
        return new ProcessingStatusResult(status(response.status()), response.currentStage(),
            response.progress(), response.message(), response.errorCode());
    }

    @Override
    public DocumentExtractionResult getResult(ExternalProcessingReference reference) {
        DocumentExtractionResult result = execute(() -> client.get()
            .uri("/api/v1/documents/{documentId}/result", reference.externalReference())
            .retrieve().body(DocumentExtractionResult.class));
        if (result == null || !reference.documentVersionId().equals(result.documentVersionId())) {
            throw new ProviderUnavailableException(
                "OpenContracts returned an invalid result schema");
        }
        mappings.synced(reference.organizationId(), reference.documentVersionId(),
            ExternalDocumentMapping.Provider.OPENCONTRACTS);
        return result;
    }

    @Override
    public void cancel(ExternalProcessingReference reference) {
        execute(() -> {
            client.post().uri("/api/v1/documents/{documentId}/cancel",
                reference.externalReference()).retrieve().toBodilessEntity();
            return Boolean.TRUE;
        });
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
                "OpenContracts returned an unknown status");
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
            throw new ProviderUnavailableException("OpenContracts request failed", exception);
        }
    }

    private interface ProviderCall<T> {
        T execute();
    }

    private record CorpusRequest(UUID organizationId, UUID projectId) {
    }
    private record CorpusResponse(String id) {
    }
    private record SubmissionRequest(UUID documentVersionId, String objectStorageKey,
                                     String originalFileName, String mimeType, String sha256,
                                     UUID correlationId) {
    }
    private record SubmissionResponse(String documentId, String status,
                                      String providerVersion) {
    }
    private record StatusResponse(String status, String currentStage, int progress,
                                  String message, String errorCode) {
    }
}
