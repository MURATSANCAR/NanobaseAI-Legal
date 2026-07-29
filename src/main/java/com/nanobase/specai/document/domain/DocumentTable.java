package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_table")
public class DocumentTable {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @Column(name = "page_start", nullable = false)
    private int pageStart;
    @Column(name = "page_end", nullable = false)
    private int pageEnd;
    @Column(length = 500)
    private String caption;
    @Column(name = "markdown_content", columnDefinition = "text")
    private String markdownContent;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_content_json", nullable = false, columnDefinition = "jsonb")
    private String structuredContentJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_boxes_json", nullable = false, columnDefinition = "jsonb")
    private String boundingBoxesJson;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentTable() {
    }

    public DocumentTable(UUID id, UUID organizationId, UUID documentVersionId,
                         int pageStart, int pageEnd, String caption, String markdownContent,
                         String structuredContentJson, String boundingBoxesJson,
                         String contentHash, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.documentVersionId = documentVersionId;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.caption = caption;
        this.markdownContent = markdownContent;
        this.structuredContentJson = structuredContentJson;
        this.boundingBoxesJson = boundingBoxesJson;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public int pageStart() { return pageStart; }
    public int pageEnd() { return pageEnd; }
    public String caption() { return caption; }
    public String markdownContent() { return markdownContent; }
    public String structuredContentJson() { return structuredContentJson; }
    public String boundingBoxesJson() { return boundingBoxesJson; }
    public String contentHash() { return contentHash; }
}
