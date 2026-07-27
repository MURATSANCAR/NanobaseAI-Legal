package com.nanobase.specai.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingEventRepository
    extends JpaRepository<ProcessingEventRecord, UUID> {
    List<ProcessingEventRecord> findAllByProcessingJobIdAndOrganizationIdOrderByOccurredAt(
        UUID jobId, UUID organizationId);
    List<ProcessingEventRecord>
        findAllByDocumentVersionIdAndOrganizationIdAndOccurredAtAfterOrderByOccurredAt(
            UUID documentVersionId, UUID organizationId, Instant occurredAfter);
    Optional<ProcessingEventRecord> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
