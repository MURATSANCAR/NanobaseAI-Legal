package com.nanobase.specai.document.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParserWarningRepository extends JpaRepository<ParserWarning, UUID> {
    List<ParserWarning> findAllByProcessingJobIdAndOrganizationIdOrderByCreatedAt(
        UUID jobId, UUID organizationId);
    void deleteAllByDocumentVersionIdAndOrganizationId(UUID versionId, UUID organizationId);
}
