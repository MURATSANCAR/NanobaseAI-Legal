package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_page")
public class DocumentPage {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @Column(name = "page_number", nullable = false, updatable = false)
    private int pageNumber;
    private BigDecimal width;
    private BigDecimal height;
    @Column(nullable = false)
    private int rotation;
    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;
    @Column(name = "normalized_text", nullable = false, columnDefinition = "text")
    private String normalizedText;
    @Column(name = "text_quality_score", precision = 6, scale = 5)
    private BigDecimal textQualityScore;
    @Column(name = "thumbnail_object_key", length = 1024)
    private String thumbnailObjectKey;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentPage() {
    }

    public DocumentPage(UUID id, UUID organizationId, UUID documentVersionId,
                        int pageNumber, BigDecimal width, BigDecimal height, int rotation,
                        String rawText, String normalizedText, BigDecimal textQualityScore,
                        String thumbnailObjectKey, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.documentVersionId = documentVersionId;
        this.pageNumber = pageNumber;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.textQualityScore = textQualityScore;
        this.thumbnailObjectKey = thumbnailObjectKey;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public int pageNumber() { return pageNumber; }
    public BigDecimal width() { return width; }
    public BigDecimal height() { return height; }
    public int rotation() { return rotation; }
    public String rawText() { return rawText; }
    public String normalizedText() { return normalizedText; }
    public BigDecimal textQualityScore() { return textQualityScore; }
    public String thumbnailObjectKey() { return thumbnailObjectKey; }
}
