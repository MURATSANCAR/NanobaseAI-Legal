package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.integration.ParserRoute.Decision;
import com.nanobase.specai.document.integration.ParserRoute.Provider;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RoutedDocumentIntelligenceAdapter implements DocumentIntelligencePort {
    private final DocumentParserRouter router;
    private final DocumentIntelligencePort docling;
    private final DocumentIntelligencePort openContracts;
    private final DisabledDocumentIntelligenceAdapter disabled =
        new DisabledDocumentIntelligenceAdapter();
    private final boolean doclingAvailable;
    private final boolean openContractsAvailable;

    public RoutedDocumentIntelligenceAdapter(
        DocumentParserRouter router,
        @Qualifier("doclingAdapter") DocumentIntelligencePort docling,
        @Qualifier("openContractsAdapter") DocumentIntelligencePort openContracts,
        @Value("${specai.document-intelligence.docling.enabled:true}")
        boolean doclingAvailable,
        @Value("${specai.document-intelligence.opencontracts.enabled:false}")
        boolean openContractsAvailable) {
        this.router = router;
        this.docling = docling;
        this.openContracts = openContracts;
        this.doclingAvailable = doclingAvailable;
        this.openContractsAvailable = openContractsAvailable;
    }

    @Override
    public ProcessingSubmission submit(DocumentProcessingCommand command) {
        ParserRoute route = router.decide(new DocumentRoutingContext(
            command.organizationId(), command.mimeType(), extension(command.originalFileName()),
            command.fileSize(), null, null, command.ocrRequired(), null,
            command.languageHint(), doclingAvailable, openContractsAvailable,
            command.annotationSynchronizationRequested(), null));
        if (route.decision() == Decision.RETRY) {
            throw new ProviderUnavailableException(route.reason());
        }
        if (route.decision() == Decision.MANUAL_REVIEW_REQUIRED) {
            return disabled.submit(command);
        }
        DocumentProcessingCommand routedCommand = new DocumentProcessingCommand(
            command.organizationId(), command.projectId(), command.documentId(),
            command.documentVersionId(), command.objectStorageKey(),
            command.originalFileName(), command.mimeType(), command.sha256(),
            command.languageHint(),
            route.ocrMode() == ParserRoute.OcrMode.FORCED || command.ocrRequired(),
            command.correlationId(), command.fileSize(),
            command.annotationSynchronizationRequested());
        return adapter(route.provider()).submit(routedCommand);
    }

    @Override
    public ProcessingStatusResult getStatus(ExternalProcessingReference reference) {
        if ("NONE".equals(reference.provider())) {
            return disabled.getStatus(reference);
        }
        return adapter(Provider.valueOf(reference.provider())).getStatus(reference);
    }

    @Override
    public DocumentExtractionResult getResult(ExternalProcessingReference reference) {
        return adapter(Provider.valueOf(reference.provider())).getResult(reference);
    }

    @Override
    public void cancel(ExternalProcessingReference reference) {
        if ("NONE".equals(reference.provider())) {
            return;
        }
        adapter(Provider.valueOf(reference.provider())).cancel(reference);
    }

    private DocumentIntelligencePort adapter(Provider provider) {
        return switch (provider) {
            case DOCLING -> docling;
            case OPENCONTRACTS -> openContracts;
            case NONE -> disabled;
        };
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
