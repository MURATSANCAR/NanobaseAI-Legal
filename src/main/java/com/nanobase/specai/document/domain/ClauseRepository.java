package com.nanobase.specai.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClauseRepository extends JpaRepository<Clause, UUID> {
    List<Clause> findAllByDocumentVersionIdAndOrganizationIdOrderBySortOrder(
        UUID versionId, UUID organizationId);
    void deleteAllByDocumentVersionIdAndOrganizationId(UUID versionId, UUID organizationId);
    Optional<Clause> findByIdAndDocumentVersionIdAndOrganizationId(
        UUID id, UUID versionId, UUID organizationId);

    @Query("""
        select clause from Clause clause
        where clause.documentVersionId = :versionId
          and clause.organizationId = :organizationId
          and (:parentClauseId is null or clause.parentClauseId = :parentClauseId)
          and (:clauseType is null or clause.clauseType = :clauseType)
          and (:pageNumber is null
               or :pageNumber between clause.pageStart and clause.pageEnd)
          and (:search is null
               or lower(coalesce(clause.title, '')) like lower(concat('%', :search, '%'))
               or lower(clause.normalizedText) like lower(concat('%', :search, '%')))
        """)
    Page<Clause> search(
        @Param("versionId") UUID versionId,
        @Param("organizationId") UUID organizationId,
        @Param("parentClauseId") UUID parentClauseId,
        @Param("clauseType") String clauseType,
        @Param("pageNumber") Integer pageNumber,
        @Param("search") String search,
        Pageable pageable);
}
