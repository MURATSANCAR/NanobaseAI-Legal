package com.nanobase.specai.document.integration;

import java.time.Instant;
import java.util.UUID;

public final class DocumentEvents {
    private DocumentEvents() {
    }

    public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID organizationId,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        T payload
    ) {
    }

    public record DocumentUploaded(
        UUID projectId,
        UUID documentId,
        UUID documentVersionId,
        String objectStorageKey,
        String mimeType,
        long fileSize
    ) {
    }
}
