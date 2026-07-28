package com.nanobase.specai.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requirement_revision")
public class RequirementRevision {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "requirement_id", nullable = false, updatable = false)
    private UUID requirementId;
    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;
    @Column(name = "snapshot_json", nullable = false, updatable = false,
        columnDefinition = "jsonb")
    private String snapshotJson;
    @Column(name = "source_type", nullable = false, updatable = false, length = 160)
    private String sourceType;
    @Column(name = "source_reference_id", updatable = false)
    private UUID sourceReferenceId;
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RequirementRevision() {
    }

    public RequirementRevision(UUID id, UUID organizationId, UUID requirementId,
                               int revisionNumber, String snapshotJson, String sourceType,
                               UUID sourceReferenceId, String createdBy, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.requirementId = requirementId;
        this.revisionNumber = revisionNumber;
        this.snapshotJson = snapshotJson;
        this.sourceType = sourceType;
        this.sourceReferenceId = sourceReferenceId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID requirementId() { return requirementId; }
    public int revisionNumber() { return revisionNumber; }
    public String snapshotJson() { return snapshotJson; }
    public String sourceType() { return sourceType; }
    public UUID sourceReferenceId() { return sourceReferenceId; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
}
