package com.nanobase.specai.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 40)
    private RequirementType requirementType = RequirementType.OTHER;
    @Enumerated(EnumType.STRING)
    @Column(name = "obligation_level", nullable = false, length = 40)
    private ObligationLevel obligationLevel = ObligationLevel.UNKNOWN;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_stage", nullable = false, length = 40)
    private LifecycleStage lifecycleStage = LifecycleStage.UNKNOWN;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RequirementCriticality criticality = RequirementCriticality.UNKNOWN;
    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_method", nullable = false, length = 40)
    private EvaluationMethod evaluationMethod = EvaluationMethod.MANUAL_REVIEW;
    @Enumerated(EnumType.STRING)
    @Column(name = "consequence_type", nullable = false, length = 40)
    private ConsequenceType consequenceType = ConsequenceType.UNKNOWN;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Remediability remediability = Remediability.UNKNOWN;
    @Column(name = "responsible_department", length = 120)
    private String responsibleDepartment;
    @Column(name = "deadline_type", length = 40)
    private String deadlineType;
    @Column(name = "explicit_deadline")
    private LocalDate explicitDeadline;
    @Column(name = "relative_deadline_days")
    private Integer relativeDeadlineDays;
    @Column(name = "scoring_weight")
    private BigDecimal scoringWeight;
    @Column(name = "minimum_score")
    private BigDecimal minimumScore;
    @Column(name = "closed_world_required", nullable = false)
    private boolean closedWorldRequired;
    @Column(name = "requires_clarification", nullable = false)
    private boolean requiresClarification;
    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_status", nullable = false, length = 40)
    private RequirementLifecycleStatus requirementStatus = RequirementLifecycleStatus.ACTIVE;
    @Column(name = "superseded_by_requirement_id")
    private UUID supersededByRequirementId;
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
        this.requirementType = RequirementType.OTHER;
        this.obligationLevel = ObligationLevel.UNKNOWN;
        this.lifecycleStage = LifecycleStage.UNKNOWN;
        this.criticality = RequirementCriticality.UNKNOWN;
        this.evaluationMethod = EvaluationMethod.MANUAL_REVIEW;
        this.consequenceType = ConsequenceType.UNKNOWN;
        this.remediability = Remediability.UNKNOWN;
        this.closedWorldRequired = false;
        this.requiresClarification = false;
        this.requirementStatus = RequirementLifecycleStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void classify(RequirementType requirementType, ObligationLevel obligationLevel,
                         LifecycleStage lifecycleStage, RequirementCriticality criticality,
                         EvaluationMethod evaluationMethod, ConsequenceType consequenceType,
                         Remediability remediability, String responsibleDepartment,
                         String deadlineType, LocalDate explicitDeadline,
                         Integer relativeDeadlineDays, BigDecimal scoringWeight,
                         BigDecimal minimumScore, boolean closedWorldRequired,
                         boolean requiresClarification, Instant now) {
        this.requirementType = requirementType == null ? RequirementType.OTHER : requirementType;
        this.obligationLevel = obligationLevel == null ? ObligationLevel.UNKNOWN : obligationLevel;
        this.lifecycleStage = lifecycleStage == null ? LifecycleStage.UNKNOWN : lifecycleStage;
        this.criticality = criticality == null ? RequirementCriticality.UNKNOWN : criticality;
        this.evaluationMethod = evaluationMethod == null
            ? EvaluationMethod.MANUAL_REVIEW : evaluationMethod;
        this.consequenceType = consequenceType == null ? ConsequenceType.UNKNOWN : consequenceType;
        this.remediability = remediability == null ? Remediability.UNKNOWN : remediability;
        this.responsibleDepartment = responsibleDepartment;
        this.deadlineType = deadlineType;
        this.explicitDeadline = explicitDeadline;
        this.relativeDeadlineDays = relativeDeadlineDays;
        this.scoringWeight = scoringWeight;
        this.minimumScore = minimumScore;
        this.closedWorldRequired = closedWorldRequired;
        this.requiresClarification = requiresClarification;
        this.updatedAt = now;
    }

    public void supersede(UUID successorId, Instant now) {
        this.requirementStatus = RequirementLifecycleStatus.SUPERSEDED;
        this.supersededByRequirementId = successorId;
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
    public RequirementType requirementType() { return requirementType; }
    public ObligationLevel obligationLevel() { return obligationLevel; }
    public LifecycleStage lifecycleStage() { return lifecycleStage; }
    public RequirementCriticality criticality() { return criticality; }
    public EvaluationMethod evaluationMethod() { return evaluationMethod; }
    public ConsequenceType consequenceType() { return consequenceType; }
    public Remediability remediability() { return remediability; }
    public String responsibleDepartment() { return responsibleDepartment; }
    public String deadlineType() { return deadlineType; }
    public LocalDate explicitDeadline() { return explicitDeadline; }
    public Integer relativeDeadlineDays() { return relativeDeadlineDays; }
    public BigDecimal scoringWeight() { return scoringWeight; }
    public BigDecimal minimumScore() { return minimumScore; }
    public boolean closedWorldRequired() { return closedWorldRequired; }
    public boolean requiresClarification() { return requiresClarification; }
    public RequirementLifecycleStatus requirementStatus() { return requirementStatus; }
    public UUID supersededByRequirementId() { return supersededByRequirementId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
