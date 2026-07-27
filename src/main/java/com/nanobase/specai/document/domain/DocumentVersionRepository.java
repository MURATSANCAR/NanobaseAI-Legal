package com.nanobase.specai.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    Optional<DocumentVersion> findByDocumentIdAndOrganizationIdAndVersionNumber(
        UUID documentId, UUID organizationId, int versionNumber);
    Optional<DocumentVersion> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<DocumentVersion> findAllByDocumentIdAndOrganizationIdOrderByVersionNumberDesc(
        UUID documentId, UUID organizationId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from DocumentVersion version where version.id = :id and version.organizationId = :organizationId")
    Optional<DocumentVersion> findForUpdate(@Param("id") UUID id,
                                             @Param("organizationId") UUID organizationId);

    @Query(value = """
        select exists(
            select 1
            from document_version version_row
            join document document_row on document_row.id = version_row.document_id
            where document_row.project_id = :projectId
              and version_row.organization_id = :organizationId
              and version_row.sha256 = :sha256
        )
        """, nativeQuery = true)
    boolean existsDuplicate(@Param("projectId") UUID projectId,
                            @Param("organizationId") UUID organizationId,
                            @Param("sha256") String sha256);
}
