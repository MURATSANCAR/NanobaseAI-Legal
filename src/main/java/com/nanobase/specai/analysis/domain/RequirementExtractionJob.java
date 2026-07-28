package com.nanobase.specai.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "requirement_extraction_job")
public class RequirementExtractionJob {
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED");

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
    @Column(nullable = false, length = 50)
    private String status;
    @Column(name = "analysis_profile_id", nullable = false, updatable = false)
    private UUID analysisProfileId;
    @Column(name = "ontology_version_id", nullable = false, updatable = false)
    private UUID ontologyVersionId;
    @Column(name = "terminology_snapshot_id", nullable = false, updatable = false)
    private UUID terminologySnapshotId;
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
    @Column(name = "total_clause_count", nullable = false)
    private int totalClauseCount;
    @Column(name = "processed_clause_count", nullable = false)
    private int processedClauseCount;
    @Column(name = "extracted_requirement_count", nullable = false)
    private int extractedRequirementCount;
    @Column(name = "manual_review_count", nullable = false)
    private int manualReviewCount;
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;
    @Column(name = "parent_job_id", updatable = false)
    private UUID parentJobId;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected RequirementExtractionJob() {
    }

    public RequirementExtractionJob(UUID id, UUID organizationId, UUID projectId,
                                    UUID documentId, UUID documentVersionId,
                                    AnalysisProfile profile, UUID terminologySnapshotId,
                                    UUID correlationId, UUID parentJobId, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.documentId = documentId;
        this.documentVersionId = documentVersionId;
        this.status = "QUEUED";
        this.analysisProfileId = profile.id();
        this.ontologyVersionId = profile.ontologyVersionId();
        this.terminologySnapshotId = terminologySnapshotId;
        this.policyVersionId = profile.policyVersionId();
        this.promptPackageVersionId = profile.promptPackageVersionId();
        this.outputSchemaVersionId = profile.outputSchemaVersionId();
        this.modelRoutingPolicyId = profile.modelRoutingPolicyId();
        this.confidencePolicyId = profile.confidencePolicyId();
        this.correlationId = correlationId;
        this.parentJobId = parentJobId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void start(int totalClauses, Instant now) {
        requireMutable();
        status = "RUNNING";
        totalClauseCount = totalClauses;
        startedAt = startedAt == null ? now : startedAt;
        updatedAt = now;
    }

    public void progress(int processed, int extracted, int reviews, Instant now) {
        requireMutable();
        processedClauseCount = Math.min(processed, totalClauseCount);
        extractedRequirementCount = extracted;
        manualReviewCount = reviews;
        updatedAt = now;
    }

    public void complete(Instant now) {
        requireMutable();
        status = "COMPLETED";
        completedAt = now;
        updatedAt = now;
    }

    public void fail(Instant now) {
        requireMutable();
        status = "FAILED";
        completedAt = now;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        requireMutable();
        status = "CANCELLED";
        completedAt = now;
        updatedAt = now;
    }

    private void requireMutable() {
        if (TERMINAL.contains(status)) {
            throw new IllegalStateException("Extraction job is already terminal");
        }
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID projectId() { return projectId; }
    public UUID documentId() { return documentId; }
    public UUID documentVersionId() { return documentVersionId; }
    public String status() { return status; }
    public UUID analysisProfileId() { return analysisProfileId; }
    public UUID ontologyVersionId() { return ontologyVersionId; }
    public UUID terminologySnapshotId() { return terminologySnapshotId; }
    public UUID policyVersionId() { return policyVersionId; }
    public UUID promptPackageVersionId() { return promptPackageVersionId; }
    public UUID outputSchemaVersionId() { return outputSchemaVersionId; }
    public UUID modelRoutingPolicyId() { return modelRoutingPolicyId; }
    public UUID confidencePolicyId() { return confidencePolicyId; }
    public int totalClauseCount() { return totalClauseCount; }
    public int processedClauseCount() { return processedClauseCount; }
    public int extractedRequirementCount() { return extractedRequirementCount; }
    public int manualReviewCount() { return manualReviewCount; }
    public UUID correlationId() { return correlationId; }
    public UUID parentJobId() { return parentJobId; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
