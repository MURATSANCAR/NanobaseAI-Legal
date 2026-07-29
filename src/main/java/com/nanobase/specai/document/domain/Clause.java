package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clause")
public class Clause {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @Column(name = "parent_clause_id")
    private UUID parentClauseId;
    @Column(name = "clause_number", length = 100)
    private String clauseNumber;
    @Column(length = 500)
    private String title;
    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;
    @Column(name = "normalized_text", nullable = false, columnDefinition = "text")
    private String normalizedText;
    @Column(name = "clause_type", length = 80)
    private String clauseType;
    @Column(name = "page_start", nullable = false)
    private int pageStart;
    @Column(name = "page_end", nullable = false)
    private int pageEnd;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_boxes_json", nullable = false, columnDefinition = "jsonb")
    private String boundingBoxesJson;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Clause() {}

    public Clause(UUID id, UUID organizationId, UUID documentVersionId, UUID parentClauseId,
                  String clauseNumber, String title, String rawText, String normalizedText,
                  String clauseType, int pageStart, int pageEnd, String boundingBoxesJson,
                  String contentHash, int sortOrder, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.documentVersionId = documentVersionId;
        this.parentClauseId = parentClauseId;
        this.clauseNumber = clauseNumber;
        this.title = title;
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.clauseType = clauseType;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.boundingBoxesJson = boundingBoxesJson;
        this.contentHash = contentHash;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID documentVersionId() { return documentVersionId; }
    public UUID parentId() { return parentClauseId; }
    public String clauseNumber() { return clauseNumber; }
    public String title() { return title; }
    public String sourceText() { return rawText; }
    public String rawText() { return rawText; }
    public String normalizedText() { return normalizedText; }
    public String clauseType() { return clauseType; }
    public int pageNumber() { return pageStart; }
    public int pageStart() { return pageStart; }
    public int pageEnd() { return pageEnd; }
    public String boundingBoxesJson() { return boundingBoxesJson; }
    public String contentHash() { return contentHash; }
    public int sortOrder() { return sortOrder; }
}
