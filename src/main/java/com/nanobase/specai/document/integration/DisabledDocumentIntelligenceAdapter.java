package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.domain.DocumentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "specai.document-intelligence.enabled",
    havingValue = "false", matchIfMissing = true)
public class DisabledDocumentIntelligenceAdapter implements DocumentIntelligencePort {
    @Override
    public DocumentProcessingResult process(DocumentProcessingCommand command) {
        return new DocumentProcessingResult(DocumentStatus.MANUAL_REVIEW_REQUIRED,
            "DOCUMENT_INTELLIGENCE_DISABLED",
            "Document intelligence is disabled; no synthetic result was generated");
    }
}
