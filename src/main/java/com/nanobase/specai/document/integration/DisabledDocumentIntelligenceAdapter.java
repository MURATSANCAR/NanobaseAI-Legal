package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.domain.DocumentStatus;

public class DisabledDocumentIntelligenceAdapter implements DocumentIntelligencePort {
    @Override
    public ProcessingSubmission submit(DocumentProcessingCommand command) {
        return new ProcessingSubmission(new ExternalProcessingReference(
            "NONE", command.correlationId().toString(), command.organizationId(),
            command.documentVersionId()), DocumentStatus.MANUAL_REVIEW_REQUIRED);
    }

    @Override
    public ProcessingStatusResult getStatus(ExternalProcessingReference reference) {
        return new ProcessingStatusResult(DocumentStatus.MANUAL_REVIEW_REQUIRED,
            "MANUAL_REVIEW_REQUIRED", 100,
            "Document intelligence is disabled; no synthetic result was generated",
            "DOCUMENT_INTELLIGENCE_DISABLED");
    }

    @Override
    public DocumentExtractionResult getResult(ExternalProcessingReference reference) {
        throw new IllegalStateException("Disabled provider has no extraction result");
    }

    @Override
    public void cancel(ExternalProcessingReference reference) {
        // A disabled provider has no remote work to cancel.
    }
}
