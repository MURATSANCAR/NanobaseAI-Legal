package com.nanobase.specai.companyfit.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompanyFitModels {
    private CompanyFitModels() {
    }

    public enum CapabilityKind {
        COMPLIANCE_CERT, BRAND_AUTH, PERSONNEL, EXPERIENCE, PRODUCT_SPEC,
        FINANCIAL, ADMINISTRATIVE, SECURITY_CTRL, OTHER
    }

    public enum FitStatus {
        MET, PARTIAL, MISSING, AMBIGUOUS, NOT_APPLICABLE
    }

    public enum OverallFit {
        FIT, CONDITIONAL, NOT_FIT, INSUFFICIENT_DATA
    }

    public record CompanyCapability(
        String capabilityId,
        String organizationId,
        CapabilityKind kind,
        String canonicalKey,
        String label,
        Map<String, Object> attributes,
        String validFrom,
        String validTo,
        String validityStatus,
        String sourceDocumentId,
        String evidenceSnippet,
        double confidence
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("capabilityId", capabilityId);
            m.put("organizationId", organizationId);
            m.put("kind", kind.name());
            m.put("canonicalKey", canonicalKey);
            m.put("label", label);
            m.put("attributes", attributes == null ? Map.of() : attributes);
            m.put("validFrom", validFrom);
            m.put("validTo", validTo);
            m.put("validityStatus", validityStatus);
            m.put("sourceDocumentId", sourceDocumentId);
            m.put("evidenceSnippet", evidenceSnippet);
            m.put("confidence", confidence);
            return m;
        }
    }

    public record FitEvidence(
        String capabilityId,
        String documentId,
        String snippet,
        String note
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("capabilityId", capabilityId);
            m.put("documentId", documentId);
            m.put("snippet", snippet);
            m.put("note", note);
            return m;
        }
    }

    public record RequirementFit(
        String requirementId,
        String category,
        String priority,
        String textPreview,
        FitStatus status,
        double score,
        List<String> reasons,
        List<FitEvidence> evidence,
        String suggestedAction
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requirementId", requirementId);
            m.put("category", category);
            m.put("priority", priority);
            m.put("textPreview", textPreview);
            m.put("status", status.name());
            m.put("score", score);
            m.put("reasons", reasons);
            m.put("evidence", evidence.stream().map(FitEvidence::toMap).toList());
            m.put("suggestedAction", suggestedAction);
            return m;
        }
    }

    public record CompanyFitReport(
        String organizationId,
        String tenderDocumentId,
        OverallFit overall,
        double overallScore,
        int mustMet,
        int mustTotal,
        List<String> missingCritical,
        List<RequirementFit> rows,
        String generatedAt,
        String policyVersion
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("organizationId", organizationId);
            m.put("tenderDocumentId", tenderDocumentId);
            m.put("overall", overall.name());
            m.put("overallScore", overallScore);
            m.put("mustMet", mustMet);
            m.put("mustTotal", mustTotal);
            m.put("missingCritical", missingCritical);
            m.put("rows", rows.stream().map(RequirementFit::toMap).toList());
            m.put("generatedAt", generatedAt);
            m.put("policyVersion", policyVersion);
            return m;
        }
    }
}
