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
@Table(name = "parser_warning")
public class ParserWarning {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @Column(name = "processing_job_id", nullable = false, updatable = false)
    private UUID processingJobId;
    @Column(name = "warning_code", nullable = false, length = 100)
    private String warningCode;
    @Column(nullable = false, length = 30)
    private String severity;
    @Column(nullable = false, length = 1000)
    private String message;
    @Column(name = "page_number")
    private Integer pageNumber;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ParserWarning() {
    }

    public ParserWarning(UUID id, UUID organizationId, UUID documentVersionId,
                         UUID processingJobId, String warningCode, String severity,
                         String message, Integer pageNumber, String metadataJson,
                         Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.documentVersionId = documentVersionId;
        this.processingJobId = processingJobId;
        this.warningCode = warningCode;
        this.severity = severity;
        this.message = message;
        this.pageNumber = pageNumber;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public String warningCode() { return warningCode; }
    public String severity() { return severity; }
    public String message() { return message; }
    public Integer pageNumber() { return pageNumber; }
    public String metadataJson() { return metadataJson; }
}
