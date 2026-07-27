package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.domain.DocumentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "specai.document-intelligence.enabled", havingValue = "true")
public class OpenContractsDocumentIntelligenceAdapter implements DocumentIntelligencePort {
    private final RestClient client;

    public OpenContractsDocumentIntelligenceAdapter(
        RestClient.Builder builder,
        @Value("${specai.document-intelligence.base-url}") String baseUrl,
        @Value("${specai.document-intelligence.api-token:}") String apiToken) {
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        if (!apiToken.isBlank()) {
            configured.defaultHeader("Authorization", "Bearer " + apiToken);
        }
        this.client = configured.build();
    }

    @Override
    public DocumentProcessingResult process(DocumentProcessingCommand command) {
        ProviderResponse response = client.post()
            .uri("/api/v1/documents/process")
            .body(new ProviderRequest(command.documentVersionId(), command.objectStorageKey(),
                command.mimeType()))
            .retrieve()
            .body(ProviderResponse.class);
        if (response == null) {
            throw new IllegalStateException("OpenContracts returned an empty response");
        }
        return new DocumentProcessingResult(DocumentStatus.valueOf(response.status()),
            response.errorCode(), response.message());
    }

    private record ProviderRequest(java.util.UUID documentVersionId, String objectStorageKey,
                                   String mimeType) {
    }

    private record ProviderResponse(String status, String errorCode, String message) {
    }
}
