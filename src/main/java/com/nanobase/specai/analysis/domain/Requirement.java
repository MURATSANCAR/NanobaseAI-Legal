package com.nanobase.specai.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requirement")
public class Requirement {
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
    @Column(name = "extraction_job_id", nullable = false, updatable = false)
    private UUID extractionJobId;
    @Column(name = "source_clause_id", nullable = false, updatable = false)
    private UUID sourceClauseId;
    @Column(name = "requirement_code", nullable = false, length = 160)
    private String requirementCode;
    @Column(length = 500)
    private String title;
    @Column(name = "requirement_text", nullable = false, columnDefinition = "text")
    private String requirementText;
    @Column(name = "normalized_requirement_text", nullable = false, columnDefinition = "text")
    private String normalizedRequirementText;
    @Column(name = "primary_concept_id")
    private UUID primaryConceptId;
    @Column(length = 160)
    private String modality;
    @Column(name = "modality_concept_id")
    private UUID modalityConceptId;
    @Column(name = "testability_status", length = 160)
    private String testabilityStatus;
    @Column(name = "condition_text", columnDefinition = "text")
    private String conditionText;
    @Column(name = "subject_text", columnDefinition = "text")
    private String subjectText;
    @Column(name = "action_text", columnDefinition = "text")
    private String actionText;
    @Column(name = "object_text", columnDefinition = "text")
    private String objectText;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "attributes_json", nullable = false, columnDefinition = "jsonb")
    private String attributesJson;
    @Column(name = "extraction_method", nullable = false, length = 160)
    private String extractionMethod;
    @Column(name = "review_status", nullable = false, length = 80)
    private String reviewStatus;
    @Column(name = "grounding_status", nullable = false, length = 80)
    private String groundingStatus;
    @Column(name = "grounding_coverage", nullable = false)
    private double groundingCoverage;
    @Column(name = "analysis_profile_id", nullable = false, updatable = false)
    private UUID analysisProfileId;
    @Column(name = "ontology_version_id", nullable = false, updatable = false)
    private UUID ontologyVersionId;
    @Column(name = "policy_version_id", nullable = false, updatable = false)
    private UUID policyVersionId;
    @Column(name = "prompt_version_id", nullable = false, updatable = false)
    private UUID promptVersionId;
    @Column(name = "model_run_id", updatable = false)
    private UUID modelRunId;
    @Column(name = "combined_confidence", nullable = false, precision = 10, scale = 6)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NUMERIC)
    private double combinedConfidence;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "explanation_json", nullable = false, columnDefinition = "jsonb")
    private String explanationJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Requirement() {
    }

    public Requirement(UUID id, UUID organizationId, UUID projectId, UUID documentId,
                       UUID documentVersionId, UUID extractionJobId, UUID sourceClauseId,
                       String requirementCode, String title, String requirementText,
                       String normalizedRequirementText, UUID primaryConceptId, String modality,
                       UUID modalityConceptId, String testabilityStatus, String conditionText,
                       String subjectText, String actionText, String objectText,
                       String attributesJson, String extractionMethod, String reviewStatus,
                       String groundingStatus, double groundingCoverage,
                       AnalysisProfile profile, UUID modelRunId, double combinedConfidence,
                       String explanationJson, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.documentId = documentId;
        this.documentVersionId = documentVersionId;
        this.extractionJobId = extractionJobId;
        this.sourceClauseId = sourceClauseId;
        this.requirementCode = requirementCode;
        this.title = title;
        this.requirementText = requirementText;
        this.normalizedRequirementText = normalizedRequirementText;
        this.primaryConceptId = primaryConceptId;
        this.modality = modality;
        this.modalityConceptId = modalityConceptId;
        this.testabilityStatus = testabilityStatus;
        this.conditionText = conditionText;
        this.subjectText = subjectText;
        this.actionText = actionText;
        this.objectText = objectText;
        this.attributesJson = attributesJson;
        this.extractionMethod = extractionMethod;
        this.reviewStatus = reviewStatus;
        this.groundingStatus = groundingStatus;
        this.groundingCoverage = groundingCoverage;
        this.analysisProfileId = profile.id();
        this.ontologyVersionId = profile.ontologyVersionId();
        this.policyVersionId = profile.policyVersionId();
        this.promptVersionId = profile.promptPackageVersionId();
        this.modelRunId = modelRunId;
        this.combinedConfidence = combinedConfidence;
        this.explanationJson = explanationJson;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void edit(String title, String requirementText, String normalizedText,
                     UUID primaryConceptId, String modality, UUID modalityConceptId,
                     String testabilityStatus, String conditionText, String subjectText,
                     String actionText, String objectText, String attributesJson,
                     String reviewStatus, Instant now) {
        this.title = title;
        this.requirementText = requirementText;
        this.normalizedRequirementText = normalizedText;
        this.primaryConceptId = primaryConceptId;
        this.modality = modality;
        this.modalityConceptId = modalityConceptId;
        this.testabilityStatus = testabilityStatus;
        this.conditionText = conditionText;
        this.subjectText = subjectText;
        this.actionText = actionText;
        this.objectText = objectText;
        this.attributesJson = attributesJson;
        this.reviewStatus = reviewStatus;
        this.updatedAt = now;
    }

    public void review(String reviewStatus, Instant now) {
        if ("APPROVED".equals(reviewStatus) && !"GROUNDED".equals(groundingStatus)) {
            throw new IllegalStateException("Only fully grounded requirements can be approved");
        }
        this.reviewStatus = reviewStatus;
        this.updatedAt = now;
    }

    public void reground(String groundingStatus, double groundingCoverage,
                         String explanationJson, Instant now) {
        this.groundingStatus = groundingStatus;
        this.groundingCoverage = groundingCoverage;
        this.explanationJson = explanationJson;
        this.updatedAt = now;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID projectId() { return projectId; }
    public UUID documentId() { return documentId; }
    public UUID documentVersionId() { return documentVersionId; }
    public UUID extractionJobId() { return extractionJobId; }
    public UUID sourceClauseId() { return sourceClauseId; }
    public String requirementCode() { return requirementCode; }
    public String title() { return title; }
    public String requirementText() { return requirementText; }
    public String normalizedRequirementText() { return normalizedRequirementText; }
    public UUID primaryConceptId() { return primaryConceptId; }
    public String modality() { return modality; }
    public UUID modalityConceptId() { return modalityConceptId; }
    public String testabilityStatus() { return testabilityStatus; }
    public String conditionText() { return conditionText; }
    public String subjectText() { return subjectText; }
    public String actionText() { return actionText; }
    public String objectText() { return objectText; }
    public String attributesJson() { return attributesJson; }
    public String extractionMethod() { return extractionMethod; }
    public String reviewStatus() { return reviewStatus; }
    public String groundingStatus() { return groundingStatus; }
    public double groundingCoverage() { return groundingCoverage; }
    public UUID analysisProfileId() { return analysisProfileId; }
    public UUID ontologyVersionId() { return ontologyVersionId; }
    public UUID policyVersionId() { return policyVersionId; }
    public UUID promptVersionId() { return promptVersionId; }
    public UUID modelRunId() { return modelRunId; }
    public double combinedConfidence() { return combinedConfidence; }
    public String explanationJson() { return explanationJson; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
