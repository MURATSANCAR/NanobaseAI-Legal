package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clause")
public class Clause {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @Column(name = "parent_id", updatable = false)
    private UUID parentId;
    @Column(name = "clause_number", nullable = false, updatable = false, length = 100)
    private String clauseNumber;
    @Column(nullable = false, updatable = false, length = 500)
    private String title;
    @Column(name = "source_text", nullable = false, updatable = false, columnDefinition = "text")
    private String sourceText;
    @Column(name = "page_number", nullable = false, updatable = false)
    private int pageNumber;
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Clause() {}

    public Clause(UUID id, UUID tenantId, UUID documentVersionId, UUID parentId,
                  String clauseNumber, String title, String sourceText,
                  int pageNumber, int sortOrder, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.documentVersionId = documentVersionId;
        this.parentId = parentId;
        this.clauseNumber = clauseNumber;
        this.title = title;
        this.sourceText = sourceText;
        this.pageNumber = pageNumber;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID parentId() { return parentId; }
    public String clauseNumber() { return clauseNumber; }
    public String title() { return title; }
    public String sourceText() { return sourceText; }
    public int pageNumber() { return pageNumber; }
    public int sortOrder() { return sortOrder; }
}
