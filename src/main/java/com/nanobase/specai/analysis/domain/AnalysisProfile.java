package com.nanobase.specai.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of every versioned input used by an extraction job.
 */
@Entity
@Table(name = "analysis_profile")
public class AnalysisProfile {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sector_context_json", nullable = false, updatable = false,
        columnDefinition = "jsonb")
    private String sectorContextJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_context_json", nullable = false, updatable = false,
        columnDefinition = "jsonb")
    private String documentContextJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "terminology_set_ids_json", nullable = false, updatable = false,
        columnDefinition = "jsonb")
    private String terminologySetIdsJson;
    @Column(name = "ontology_version_id", nullable = false, updatable = false)
    private UUID ontologyVersionId;
    @Column(name = "policy_version_id", nullable = false, updatable = false)
    private UUID policyVersionId;
    @Column(name = "prompt_package_version_id", nullable = false, updatable = false)
    private UUID promptPackageVersionId;
    @Column(name = "output_schema_version_id", nullable = false, updatable = false)
    private UUID outputSchemaVersionId;
    @Column(name = "model_routing_policy_id", nullable = false, updatable = false)
    private UUID modelRoutingPolicyId;
    @Column(name = "confidence_policy_id", nullable = false, updatable = false)
    private UUID confidencePolicyId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, updatable = false,
        columnDefinition = "jsonb")
    private String snapshotJson;
    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AnalysisProfile() {
    }

    public AnalysisProfile(UUID id, UUID organizationId, UUID projectId, UUID documentId,
                           UUID documentVersionId, String sectorContextJson,
                           String documentContextJson, String terminologySetIdsJson,
                           UUID ontologyVersionId, UUID policyVersionId,
                           UUID promptPackageVersionId, UUID outputSchemaVersionId,
                           UUID modelRoutingPolicyId, UUID confidencePolicyId,
                           String snapshotJson, String contentHash, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.documentId = documentId;
        this.documentVersionId = documentVersionId;
        this.sectorContextJson = sectorContextJson;
        this.documentContextJson = documentContextJson;
        this.terminologySetIdsJson = terminologySetIdsJson;
        this.ontologyVersionId = ontologyVersionId;
        this.policyVersionId = policyVersionId;
        this.promptPackageVersionId = promptPackageVersionId;
        this.outputSchemaVersionId = outputSchemaVersionId;
        this.modelRoutingPolicyId = modelRoutingPolicyId;
        this.confidencePolicyId = confidencePolicyId;
        this.snapshotJson = snapshotJson;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID projectId() { return projectId; }
    public UUID documentId() { return documentId; }
    public UUID documentVersionId() { return documentVersionId; }
    public String sectorContextJson() { return sectorContextJson; }
    public String documentContextJson() { return documentContextJson; }
    public String terminologySetIdsJson() { return terminologySetIdsJson; }
    public UUID ontologyVersionId() { return ontologyVersionId; }
    public UUID policyVersionId() { return policyVersionId; }
    public UUID promptPackageVersionId() { return promptPackageVersionId; }
    public UUID outputSchemaVersionId() { return outputSchemaVersionId; }
    public UUID modelRoutingPolicyId() { return modelRoutingPolicyId; }
    public UUID confidencePolicyId() { return confidencePolicyId; }
    public String snapshotJson() { return snapshotJson; }
    public String contentHash() { return contentHash; }
    public Instant createdAt() { return createdAt; }
}
